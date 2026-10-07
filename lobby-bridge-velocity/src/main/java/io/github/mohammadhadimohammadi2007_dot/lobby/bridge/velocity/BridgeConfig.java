package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings from {@code plugins/lobby-bridge/config.toml}.
 *
 * @param lobbyServers          backend server names that are lobbies
 * @param updateIntervalSeconds how often snapshots are pushed
 * @param groups                group name to server names, in file order
 */
record BridgeConfig(Set<String> lobbyServers, int updateIntervalSeconds, Map<String, List<String>> groups) {

    private static final String FILE_NAME = "config.toml";
    private static final int MIN_INTERVAL = 1;
    private static final int MAX_INTERVAL = 60;
    private static final int DEFAULT_INTERVAL = 2;

    /** Creates the default file if needed, then reads it. Problems are logged and defaults are used. */
    static BridgeConfig load(Path dataDir, Logger logger) throws IOException {
        Path file = dataDir.resolve(FILE_NAME);
        if (Files.notExists(file)) {
            Files.createDirectories(dataDir);
            try (InputStream in = BridgeConfig.class.getResourceAsStream("/" + FILE_NAME)) {
                if (in == null) {
                    throw new IOException("Default " + FILE_NAME + " is missing from the plugin jar");
                }
                Files.copy(in, file);
            }
            logger.info("Created default {}", file);
        }

        Toml toml = new Toml().read(file.toFile());
        List<String> lobbies = toml.getList("lobby-servers", List.of("lobby"));

        long interval = toml.getLong("update-interval", (long) DEFAULT_INTERVAL);
        if (interval < MIN_INTERVAL || interval > MAX_INTERVAL) {
            logger.warn("config.toml: 'update-interval' must be from {} to {}, got {}. Using {}.",
                    MIN_INTERVAL, MAX_INTERVAL, interval, DEFAULT_INTERVAL);
            interval = DEFAULT_INTERVAL;
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        Toml groupTable = toml.getTable("groups");
        if (groupTable != null) {
            for (Map.Entry<String, Object> entry : groupTable.entrySet()) {
                if (entry.getValue() instanceof List<?> servers) {
                    groups.put(entry.getKey(), servers.stream().map(String::valueOf).toList());
                } else {
                    logger.warn("config.toml: group '{}' must be a list like [\"server-1\", \"server-2\"]. Skipped.",
                            entry.getKey());
                }
            }
        }
        return new BridgeConfig(Set.copyOf(lobbies), (int) interval, groups);
    }
}
