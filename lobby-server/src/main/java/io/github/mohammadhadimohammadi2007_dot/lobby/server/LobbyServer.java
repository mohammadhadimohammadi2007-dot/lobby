package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeConnector;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BungeeConnector;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.ClientCapabilities;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSystem;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ConsoleInput;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.LobbiesCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.LobbyCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.SpawnCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.AuthFactory;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.PlayerLimitListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ServerListListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.DisplayService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor.NetworkCheckService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.DisplayLoad;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.VisibilityService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstances;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbySelectorMenu;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.LiteBansService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.luckperms.LuckPermsIntegration;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.signedvelocity.SignedVelocityReceiver;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.LiveSkinUpdater;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.MojangSkinFetcher;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.OfflineSkinListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinsRestorerReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.movement.FlyCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.movement.MovementService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcSkins;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.PortalCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.PortalService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.protection.ProtectionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.TickStats;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldLoader;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
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
import java.util.Locale;
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
    private LobbyInstances lobbies;
    private DatabasePool database;
    private LiteBansService liteBans;
    private SkinsRestorerReader skinsRestorer;
    private BridgeService bridge;
    private ChatSystem chat;
    private ActionServices actions;
    private MenuService menus;
    private ClientObjectRenderer display;
    private ClientObjectClicks clicks;
    private HologramService holograms;
    private NpcService npcs;
    private TeamManager teams;
    private DisplayService displays;
    private HotbarService hotbar;
    private final RegionTracker regions = new RegionTracker();
    private MovementService movement;
    private PortalService portals;
    private NetworkCheckService networkCheck;
    private final PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
    private final LobbyText text;

    public LobbyServer(ConfigManager configManager) {
        this.configManager = configManager;
        this.text = new LobbyText(configManager, placeholders);
    }

    /** Boots everything in order and opens the port. Players can only join after the map is loaded. */
    public void start() {
        long startNanos = System.nanoTime();
        LobbyConfig config = configManager.current().config();

        MinecraftServer minecraftServer = MinecraftServer.init(AuthFactory.create(config.connection()));
        MinecraftServer.setBrandName(BRAND);
        LOGGER.info("Minecraft version {} (protocol {})", MinecraftServer.VERSION_NAME, MinecraftServer.PROTOCOL_VERSION);

        build();
        MinecraftServer.getSchedulerManager().buildShutdownTask(this::shutdown);

        minecraftServer.start(config.server().host(), config.server().port());
        ConsoleInput.start();

        printSummary(config, (System.nanoTime() - startNanos) / NANOS_PER_MILLI);
    }

    /**
     * Everything between starting Minecraft and opening the port: map, integrations, features, listeners
     * and commands. Package-private so the load tests can run the real lobby inside a test server.
     */
    void build() {
        ConfigSnapshot snapshot = configManager.current();
        LobbyConfig config = snapshot.config();
        world = WorldLoader.load(config.world(), SpawnListener.spawnPosition(config));
        // Minestom sets up its entity classes (metadata tables, time units) when the first entity is made.
        // Holograms and NPCs are only packets, so without this the first player paid for it during their join.
        new LivingEntity(EntityType.PLAYER);

        startIntegrations(snapshot);
        // The instances need the permission service (to let staff into a full lobby) and messages.
        lobbies = LobbyInstances.create(world.instance(), configManager, text, permissions);
        startPlaceholders();
        startActions(snapshot);
        startChat(snapshot);
        startDisplays();
        startHotbar();
        startMovement();
        startNetworkCheck();
        registerListeners();
        registerCommands();
        configManager.onReload(this::applyReload);
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
                liteBans = LiteBansService.start(database, settings.liteBans(), configManager, text);
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

    /** Registers the built-in placeholders and keeps their caches fresh. */
    private void startPlaceholders() {
        BuiltinPlaceholders.registerAll(placeholders.registry(),
                new BuiltinPlaceholders.Sources(configManager, permissions, muteService, bridge, this, lobbies));
        // Rank changes (LuckPerms) drop the player's cached prefix, suffix and permissions.
        permissions.onMetaChange(placeholders::invalidate);
    }

    /**
     * The action system and the menus, which the lobby's own features and later phases plug into.
     * Server switching goes through the bridge behind Velocity, the BungeeCord channel behind
     * BungeeCord, and is simply not available in standalone mode.
     */
    private void startActions(ConfigSnapshot snapshot) {
        actions = new ActionServices(configManager, text, permissions, bridge);
        if (bridge.enabled()) {
            actions.connector(new BridgeConnector(bridge, text));
            LiveSkinUpdater.register(bridge);
        } else if (snapshot.config().connection().mode() == ConnectionMode.BUNGEECORD) {
            actions.connector(new BungeeConnector(text));
        }
        menus = new MenuService(() -> configManager.current().menus(), text, actions, bridge);
        menus.addBuiltIn(LobbySelectorMenu.NAME, new LobbySelectorMenu(configManager, lobbies, bridge.networkState())::build);
        actions.menus(menus);
        actions.lobbies(lobbies);
    }

    /**
     * The display layer: holograms (and later NPCs) shown with packets only, and the clicks on them.
     * Everything here belongs to the lobby map, so it appears in every lobby instance of it.
     */
    private void startDisplays() {
        display = new ClientObjectRenderer();
        // Read on every spawn, so /lobby reload changes it for the next NPC that appears.
        display.temporaryListingDelay(() -> configManager.current().config().npcs().legacyTabRemovalDelayMillis());
        clicks = new ClientObjectClicks(display);
        // The one place teams are made; NPCs use it to hide the player-list name above their heads.
        teams = new TeamManager(player -> bridge.capabilities(player).protocolVersion(), false);
        Hologram.Services displayServices = new Hologram.Services(text, permissions,
                player -> bridge.capabilities(player).legacy(), WorldScope.mainMap(world.instance()),
                player -> bridge.capabilities(player).protocolVersion() >= ProtocolVersions.V1_20_5);
        holograms = HologramService.start(configManager.dataDir(), display, displayServices, actions);
        // NPC skins from Mojang work in every mode; the lookups run on virtual threads and are cached.
        NpcSkins skins = new NpcSkins(new MojangSkinFetcher(), skinsRestorer,
                configManager.current().integrations().mineSkin().apiKey());
        npcs = NpcService.start(configManager.dataDir(), display, displayServices, actions, skins, teams);
        clicks.addHandler(holograms);
        clicks.addHandler(npcs);
        // Scoreboard, tab list, nametags and bars, on a thread of their own.
        displays = new DisplayService(configManager, text, permissions, teams,
                player -> bridge.capabilities(player).protocolVersion());
    }

    /** Hotbar items and player visibility; the visibility choice is kept with the players' chat settings. */
    private void startHotbar() {
        VisibilityService visibility = new VisibilityService(configManager, permissions, chat.settings(), text);
        hotbar = new HotbarService(configManager, text, actions, bridge, visibility);
    }

    /** Double jump, /fly, jump pads, launch pads and portals; the pads and portals are regions of the map. */
    private void startMovement() {
        movement = new MovementService(configManager, permissions, regions);
        portals = PortalService.start(configManager.dataDir(), regions, actions, text);
    }

    /**
     * Checks the group and server names used anywhere against what the proxy bridge reports, when it first
     * reports and after every reload.
     */
    private void startNetworkCheck() {
        networkCheck = new NetworkCheckService(configManager, bridge.networkState(),
                new NetworkCheckService.Sources(holograms::all, npcs::all, portals::all));
        networkCheck.start();
    }

    /** The chat system; SignedVelocity verdicts are only trusted behind Velocity. */
    private void startChat(ConfigSnapshot snapshot) {
        SignedVelocityReceiver signedVelocity = null;
        if (snapshot.integrations().signedVelocity().enabled()) {
            if (snapshot.config().connection().mode() == ConnectionMode.VELOCITY) {
                signedVelocity = new SignedVelocityReceiver();
                integrations.add(IntegrationStatus.active("SignedVelocity", "chat and command verdicts from the proxy"));
            } else {
                integrations.add(IntegrationStatus.failed("SignedVelocity", "only works with mode: velocity"));
            }
        }
        chat = ChatSystem.start(new ChatSystem.Dependencies(configManager, text, permissions, muteService, bridge,
                database, signedVelocity));
    }

    private void registerListeners() {
        EventNode<Event> global = MinecraftServer.getGlobalEventHandler();
        EventNode<PlayerEvent> playerEvents = EventNode.type("lobby-players", EventFilter.PLAYER);
        global.addChild(playerEvents);

        new SpawnListener(configManager, lobbies::forJoin).register(playerEvents);
        bridge.register(playerEvents);
        placeholders.register(playerEvents);
        chat.register(playerEvents, MinecraftServer.getCommandManager());
        menus.register(playerEvents);
        display.register(playerEvents);
        teams.register(playerEvents);
        displays.register(playerEvents);
        hotbar.register(playerEvents);
        regions.register(playerEvents);
        movement.register(playerEvents);
        portals.register(playerEvents);
        clicks.register(playerEvents);
        new PlayerLimitListener(configManager, text).register(global);
        new ProtectionListener(configManager, permissions).register(global);
        new ServerListListener(configManager, text).register();
        tickStats.register();
    }

    private void registerCommands() {
        var commands = MinecraftServer.getCommandManager();
        commands.register(new SpawnCommand(configManager, text, permissions));
        commands.register(new LobbyCommand(configManager, text, permissions, this, lobbies));
        commands.register(new LobbiesCommand(text, permissions, menus));
        commands.register(new HologramCommand(holograms, text, permissions, configManager.dataDir(), npcs));
        commands.register(new NpcCommand(npcs, text, permissions, configManager.dataDir()));
        commands.register(new FlyCommand(configManager, text, permissions, movement));
        commands.register(new PortalCommand(portals, text, permissions));
    }

    /** Applies the options that can change while running. Called after every successful reload. */
    private void applyReload(ConfigSnapshot reloaded) {
        // Config values used by placeholders (operators, server name...) may have changed.
        placeholders.invalidateAll();
        chat.reload(reloaded);
        holograms.reload();
        npcs.reload();
        portals.reload();
        movement.reload();
        // Async: it reads every hologram, NPC and portal, which can take a moment on a big lobby.
        Async.run(networkCheck::run);
        // Messages and placeholders may have changed, so every hologram is built again.
        display.invalidateAll();
        Async.onTickThread(() -> {
            lobbies.applyWorldRules(reloaded.config().world());
            hotbar.giveAll();
            // The operators list may have changed, which changes which commands players can see.
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.refreshCommands();
            }
        });
    }

    private void printSummary(LobbyConfig config, long startMillis) {
        LOGGER.info("Mode: {}", AuthFactory.describe(config.connection()));
        LOGGER.info("Map: {} ({}, {} chunks)", world.name(), world.format().name().toLowerCase(), world.chunkCount());
        LOGGER.info("Lobbies: {} instance(s) of that map", lobbies.count());
        LOGGER.info("Permissions: {}", permissions.name());
        LOGGER.info("Bridge: {}", bridge.status());
        LOGGER.info("Chat: {}", chat.describe());
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
        stopFeatures();
        Async.shutdown();
        LOGGER.info("Goodbye!");
    }

    /** Stops and saves every feature; package-private so tests can stop a lobby without stopping {@link Async}. */
    void stopFeatures() {
        chat.shutdown();
        holograms.shutdown();
        npcs.shutdown();
        portals.shutdown();
        displays.shutdown();
        display.shutdown();
        permissions.shutdown();
        if (liteBans != null) {
            liteBans.shutdown();
        }
        if (database != null) {
            database.close();
            LOGGER.info("Database connections closed.");
        }
    }

    /** Whether players are muted (LiteBans), for the chat system. */
    public MuteService muteService() {
        return muteService;
    }

    /** SkinsRestorer skins for NPCs, or {@code null} if that integration is off. */
    public SkinsRestorerReader skinsRestorer() {
        return skinsRestorer;
    }

    /** Placeholders and message rendering, for later phases. */
    public LobbyText text() {
        return text;
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

    @Override
    public String networkCheck() {
        if (!bridge.enabled()) {
            return "not checked (no proxy bridge in this mode)";
        }
        return networkCheck.last().summary();
    }

    @Override
    public String displayLoad() {
        DisplayLoad.Snapshot objects = display.load();
        DisplayLoad.Snapshot board = displays.load();
        return String.format(Locale.ROOT, "holograms and NPCs %.1f%% busy, longest cycle %.1f ms (%d holograms, %d NPCs);"
                        + " scoreboard and tab %.1f%% busy, longest cycle %.1f ms, %d refreshes skipped (last minute)",
                objects.busyPercent(), objects.longestCycleMillis(), holograms.count(), npcs.count(),
                board.busyPercent(), board.longestCycleMillis(), displays.skippedRefreshes());
    }

    @Override
    public String clientOf(Player player) {
        ClientCapabilities client = bridge.capabilities(player);
        return ProtocolVersions.name(client.protocolVersion()) + " (protocol " + client.protocolVersion() + "), "
                + client.tier().name().toLowerCase(Locale.ROOT)
                + (client.reported() ? ", reported by the proxy" : ", not reported by a proxy (this server's own version)");
    }

    /** The lobby instances of this server. */
    public LobbyInstances lobbies() {
        return lobbies;
    }

    /** The chat system (pipeline, settings, filter). */
    public ChatSystem chat() {
        return chat;
    }

    /** Shared actions for NPCs, holograms, menus, hotbar items and portals. */
    public ActionServices actions() {
        return actions;
    }

    /** The NPCs of this server. */
    public NpcService npcs() {
        return npcs;
    }

    /** The holograms of this server. */
    public HologramService holograms() {
        return holograms;
    }

    /** The menus from menus.yml. */
    public MenuService menus() {
        return menus;
    }

    /** Client versions and network player counts from the proxy bridge. */
    public BridgeService bridge() {
        return bridge;
    }
}
