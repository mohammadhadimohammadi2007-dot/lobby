package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ConsoleInput;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.LobbyCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.SpawnCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.AuthFactory;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.PlayerLimitListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ServerListListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.LiteBansService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.luckperms.LuckPermsIntegration;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.MojangSkinFetcher;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.OfflineSkinListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinsRestorerReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.protection.ProtectionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.TickStats;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldLoader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldRules;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Starts and stops the lobby: connection mode, map, listeners, commands and integrations.
 * Created once by {@link LobbyMain}.
 */
public final class LobbyServer implements ServerInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyServer.class);
    private static final String BRAND = "Lobby";
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String LUCKPERMS = "LuckPerms";
    private static final String LITEBANS = "LiteBans";
    private static final String SKINSRESTORER = "SkinsRestorer";

    private final ConfigManager configManager;
    private final TickStats tickStats = new TickStats();
    private final List<IntegrationStatus> integrations = new ArrayList<>();
    private PermissionService permissions;
    private MuteService muteService = MuteService.NONE;
    private LobbyWorld world;
    private DatabasePool database;
    private LiteBansService liteBans;
    private SkinsRestorerReader skinsRestorer;
    private BridgeService bridge;

    public LobbyServer(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Boots everything in order and opens the port. Players can only join after the map is loaded. */
    public void start() {
        long startNanos = System.nanoTime();
        ConfigSnapshot snapshot = configManager.current();
        LobbyConfig config = snapshot.config();

        MinecraftServer minecraftServer = MinecraftServer.init(AuthFactory.create(config.connection()));
        MinecraftServer.setBrandName(BRAND);
        LOGGER.info("Minecraft version {} (protocol {})", MinecraftServer.VERSION_NAME, MinecraftServer.PROTOCOL_VERSION);

        world = WorldLoader.load(config.world(), SpawnListener.spawnPosition(config));
        WorldRules.apply(world.instance(), config.world());

        startIntegrations(snapshot);
        registerListeners();
        registerCommands();
        configManager.onReload(this::applyReload);
        MinecraftServer.getSchedulerManager().buildShutdownTask(this::shutdown);

        minecraftServer.start(config.server().host(), config.server().port());
        ConsoleInput.start();

        printSummary(config, (System.nanoTime() - startNanos) / NANOS_PER_MILLI);
    }

    /**
     * Starts each enabled integration. A failing integration is reported and skipped; the lobby
     * always starts. Runs before the port opens, so blocking database calls are fine here.
     */
    private void startIntegrations(ConfigSnapshot snapshot) {
        IntegrationsConfig settings = snapshot.integrations();
        LobbyConfig.Connection connection = snapshot.config().connection();
        EventNode<Event> global = MinecraftServer.getGlobalEventHandler();

        if (settings.needsDatabase()) {
            try {
                database = DatabasePool.connect(settings.database());
                LOGGER.info("Connected to the database {}", settings.database());
            } catch (SQLException e) {
                LOGGER.error("{}", e.getMessage());
                LOGGER.error("Database integrations are turned off until this is fixed and the server restarts.");
            }
        }

        permissions = startLuckPerms(settings);

        if (!settings.liteBans().enabled()) {
            integrations.add(IntegrationStatus.disabled(LITEBANS));
        } else if (database == null) {
            integrations.add(IntegrationStatus.failed(LITEBANS, "no database connection"));
        } else {
            try {
                liteBans = LiteBansService.start(database, settings.liteBans(), configManager);
                boolean checkBans = connection.mode() == ConnectionMode.STANDALONE;
                liteBans.register(global, checkBans);
                muteService = liteBans;
                integrations.add(IntegrationStatus.active(LITEBANS, checkBans ? "bans and mutes" : "mutes"));
            } catch (SQLException e) {
                integrations.add(IntegrationStatus.failed(LITEBANS, e.getMessage()));
            }
        }

        if (!settings.skinsRestorer().enabled()) {
            integrations.add(IntegrationStatus.disabled(SKINSRESTORER));
        } else if (database == null) {
            integrations.add(IntegrationStatus.failed(SKINSRESTORER, "no database connection"));
        } else {
            try {
                SkinsRestorerReader reader = new SkinsRestorerReader(database, settings.skinsRestorer().tablePrefix());
                reader.checkTables();
                skinsRestorer = reader;
                integrations.add(IntegrationStatus.active(SKINSRESTORER, "read only"));
            } catch (SQLException e) {
                integrations.add(IntegrationStatus.failed(SKINSRESTORER, e.getMessage()));
            }
        }

        boolean bridgeEnabled = settings.bridge().enabled();
        if (bridgeEnabled && !connection.mode().behindProxy()) {
            LOGGER.warn("The bridge is enabled but mode is standalone, so it is ignored (there is no proxy to trust).");
        }
        bridge = new BridgeService(bridgeEnabled && connection.mode().behindProxy());

        // Standalone offline mode is the only mode where nobody else provides skins.
        boolean offlineStandalone = connection.mode() == ConnectionMode.STANDALONE && !connection.onlineMode();
        MojangSkinFetcher mojang = connection.fetchSkinsForOfflinePlayers() ? new MojangSkinFetcher() : null;
        if (offlineStandalone && (skinsRestorer != null || mojang != null)) {
            new OfflineSkinListener(skinsRestorer, mojang).register(global);
        }
    }

    /** LuckPerms if enabled and included in this build; otherwise the operators list. */
    private PermissionService startLuckPerms(IntegrationsConfig settings) {
        PermissionService operators = new OperatorPermissionService(configManager);
        if (!settings.luckPerms().enabled()) {
            integrations.add(IntegrationStatus.disabled(LUCKPERMS));
            return operators;
        }
        Optional<LuckPermsIntegration> luckPerms = LuckPermsIntegration.find();
        if (luckPerms.isEmpty()) {
            integrations.add(IntegrationStatus.failed(LUCKPERMS, "LuckPerms support is missing from this jar"
                    + " (was it built without the lobby-luckperms module?). Using the operators list instead"));
            return operators;
        }
        try {
            PermissionService service = luckPerms.get().start(configManager.dataDir(), settings.database(), settings.luckPerms());
            integrations.add(IntegrationStatus.active(LUCKPERMS, "messaging: " + settings.luckPerms().messagingService()));
            return service;
        } catch (Exception | LinkageError e) {
            LOGGER.error("LuckPerms could not start", e);
            integrations.add(IntegrationStatus.failed(LUCKPERMS, e.getMessage() + ". Using the operators list instead"));
            return operators;
        }
    }

    private void registerListeners() {
        EventNode<Event> global = MinecraftServer.getGlobalEventHandler();
        EventNode<PlayerEvent> playerEvents = EventNode.type("lobby-players", EventFilter.PLAYER);
        global.addChild(playerEvents);

        new SpawnListener(configManager, world.instance()).register(playerEvents);
        bridge.register(playerEvents);
        new PlayerLimitListener(configManager).register(global);
        new ProtectionListener(configManager, permissions).register(global);
        new ServerListListener(configManager).register();
        tickStats.register();
    }

    private void registerCommands() {
        var commands = MinecraftServer.getCommandManager();
        commands.register(new SpawnCommand(configManager, permissions));
        commands.register(new LobbyCommand(configManager, permissions, this));
    }

    /** Applies the options that can change while running. Called after every successful reload. */
    private void applyReload(ConfigSnapshot reloaded) {
        Async.onTickThread(() -> {
            WorldRules.apply(world.instance(), reloaded.config().world());
            // The operators list may have changed, which changes which commands players can see.
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.refreshCommands();
            }
        });
    }

    private void printSummary(LobbyConfig config, long startMillis) {
        LOGGER.info("Mode: {}", AuthFactory.describe(config.connection()));
        LOGGER.info("Map: {} ({}, {} chunks)", world.name(), world.format().name().toLowerCase(), world.chunkCount());
        LOGGER.info("Permissions: {}", permissions.name());
        LOGGER.info("Bridge: {}", bridge.status());
        for (IntegrationStatus status : integrations) {
            if (status.state() == IntegrationStatus.State.FAILED) {
                LOGGER.error("{}", status.describe());
            } else {
                LOGGER.info("{}", status.describe());
            }
        }
        AuthFactory.logSafetyWarnings(config.connection(), config.server().port());
        if (config.connection().mode() == ConnectionMode.STANDALONE && !config.connection().onlineMode()
                && !config.operators().isEmpty()) {
            LOGGER.warn("The operators list uses usernames. In offline mode anyone can join with an operator's name"
                    + " and get every permission.");
        }
        LOGGER.info("Ready in {} ms. Listening on {}:{}. Type 'stop' to shut down.",
                startMillis, config.server().host(), config.server().port());
    }

    /** Runs once when the server stops (the 'stop' command, Ctrl+C or a kill signal). */
    private void shutdown() {
        LOGGER.info("Shutting down...");
        permissions.shutdown();
        if (liteBans != null) {
            liteBans.shutdown();
        }
        if (database != null) {
            database.close();
            LOGGER.info("Database connections closed.");
        }
        Async.shutdown();
        LOGGER.info("Goodbye!");
    }

    /** Whether players are muted (LiteBans), for the chat system. */
    public MuteService muteService() {
        return muteService;
    }

    /** SkinsRestorer skins for NPCs, or {@code null} if that integration is off. */
    public SkinsRestorerReader skinsRestorer() {
        return skinsRestorer;
    }

    /** Permission checks and rank meta. */
    public PermissionService permissions() {
        return permissions;
    }

    @Override
    public String modeDescription() {
        return AuthFactory.describe(configManager.current().config().connection());
    }

    @Override
    public double tps() {
        return tickStats.tps();
    }

    @Override
    public double mspt() {
        return tickStats.mspt();
    }

    @Override
    public LobbyWorld world() {
        return world;
    }

    @Override
    public List<IntegrationStatus> integrations() {
        return List.copyOf(integrations);
    }

    @Override
    public String bridgeStatus() {
        return bridge.status();
    }

    /** Client versions and network player counts from the proxy bridge. */
    public BridgeService bridge() {
        return bridge;
    }
}
