package io.github.mohammadhadimohammadi2007_dot.lobby.server;

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
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Starts and stops the lobby: connection mode, map, listeners, commands and integrations.
 * Created once by {@link LobbyMain}.
 */
public final class LobbyServer implements ServerInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyServer.class);
    private static final String BRAND = "Lobby";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final ConfigManager configManager;
    private final TickStats tickStats = new TickStats();
    private final List<IntegrationStatus> integrations = new ArrayList<>();
    private PermissionService permissions;
    private LobbyWorld world;

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

    private void startIntegrations(ConfigSnapshot snapshot) {
        permissions = new OperatorPermissionService(configManager);
        integrations.add(IntegrationStatus.disabled("LuckPerms"));
        integrations.add(IntegrationStatus.disabled("LiteBans"));
        integrations.add(IntegrationStatus.disabled("SkinsRestorer"));
    }

    private void registerListeners() {
        EventNode<Event> global = MinecraftServer.getGlobalEventHandler();
        EventNode<PlayerEvent> playerEvents = EventNode.type("lobby-players", EventFilter.PLAYER);
        global.addChild(playerEvents);

        new SpawnListener(configManager, world.instance()).register(playerEvents);
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
        integrations.forEach(status -> LOGGER.info("{}", status.describe()));
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
        Async.shutdown();
        LOGGER.info("Goodbye!");
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
        return "disabled";
    }
}
