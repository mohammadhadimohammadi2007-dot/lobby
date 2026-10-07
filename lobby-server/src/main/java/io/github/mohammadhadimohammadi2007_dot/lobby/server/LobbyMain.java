package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Program entry point: {@code java -jar lobby-server.jar}. */
public final class LobbyMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyMain.class);
    private static final int EXIT_CONFIG_ERROR = 1;
    private static final int EXIT_STARTUP_ERROR = 2;

    private LobbyMain() {
    }

    public static void main(String[] args) {
        StartupBanner.print();
        Path dataDir = Path.of("").toAbsolutePath();

        ConfigManager configManager = new ConfigManager(dataDir);
        ConfigSnapshot snapshot;
        try {
            snapshot = configManager.load();
        } catch (ConfigException e) {
            LOGGER.error("Cannot start: {}", e.getMessage());
            LOGGER.error("Fix the problem above and start the server again.");
            System.exit(EXIT_CONFIG_ERROR);
            return;
        }

        // Minestom reads these once, when its classes load, so they must be set before anything else.
        System.setProperty("minestom.chunk-view-distance", String.valueOf(snapshot.config().world().viewDistance()));

        try {
            new LobbyServer(configManager).start();
        } catch (RuntimeException e) {
            LOGGER.error("The server failed to start", e);
            System.exit(EXIT_STARTUP_ERROR);
        }
    }
}
