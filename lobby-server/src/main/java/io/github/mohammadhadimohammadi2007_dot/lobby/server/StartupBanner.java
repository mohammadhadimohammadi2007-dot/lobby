package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prints the name and version when the server starts. */
final class StartupBanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupBanner.class);
    private static final String LINE = "------------------------------------------------------------";

    private StartupBanner() {
    }

    static void print() {
        LOGGER.info(LINE);
        LOGGER.info("  Lobby server {}", version());
        LOGGER.info("  Java {} on {}", Runtime.version(), System.getProperty("os.name"));
        LOGGER.info(LINE);
    }

    /** Version from the jar manifest, or "dev" when running from an IDE or Gradle. */
    static String version() {
        String version = StartupBanner.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
