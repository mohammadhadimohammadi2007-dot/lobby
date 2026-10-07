package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ConsoleInput;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.AuthFactory;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.PlayerLimitListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ServerListListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.TickStats;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldLoader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldRules;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts and stops the lobby: connection mode, map, listeners, commands and integrations.
 * Created once by {@link LobbyMain}.
 */
public final class LobbyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyServer.class);
    private static final String BRAND = "Lobby";

    private final ConfigManager configManager;
    private final TickStats tickStats = new TickStats();
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

        EventNode<PlayerEvent> playerEvents = EventNode.type("lobby-players", EventFilter.PLAYER);
        MinecraftServer.getGlobalEventHandler().addChild(playerEvents);
        new SpawnListener(configManager, world.instance()).register(playerEvents);
        new PlayerLimitListener(configManager).register(MinecraftServer.getGlobalEventHandler());
        new ServerListListener(configManager).register();
        tickStats.register();

        configManager.onReload(reloaded -> Async.onTickThread(
                () -> WorldRules.apply(world.instance(), reloaded.config().world())));

        MinecraftServer.getSchedulerManager().buildShutdownTask(this::shutdown);

        minecraftServer.start(config.server().host(), config.server().port());
        ConsoleInput.start();

        AuthFactory.logSafetyWarnings(config.connection(), config.server().port());
        long startMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        LOGGER.info("Mode: {}", AuthFactory.describe(config.connection()));
        LOGGER.info("Ready in {} ms. Listening on {}:{}. Type 'stop' to shut down.",
                startMillis, config.server().host(), config.server().port());
    }

    /** Runs once when the server stops (the 'stop' command, Ctrl+C or a kill signal). */
    private void shutdown() {
        LOGGER.info("Shutting down...");
        Async.shutdown();
        LOGGER.info("Goodbye!");
    }

    /** The loaded map. */
    public LobbyWorld world() {
        return world;
    }

    /** TPS and MSPT measurements. */
    public TickStats tickStats() {
        return tickStats;
    }
}
