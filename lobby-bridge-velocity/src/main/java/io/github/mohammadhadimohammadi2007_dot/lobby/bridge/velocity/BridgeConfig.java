package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings from {@code plugins/lobby-bridge/config.toml}.
 *
 * @param lobbyServers          backend server names that are lobbies, in file order
 * @param updateIntervalSeconds how often snapshots are pushed
 * @param statusIntervalSeconds how often servers are pinged
 * @param allowedCommands       first words of console commands lobbies may request, lower case
 * @param groups                group name to server names, in file order (always has {@code lobbies})
 * @param strategies            group name to how its server is picked; groups not listed use least-players
 * @param joinFullPermission    permission that lets a player join a server that is already full
 */
record BridgeConfig(Set<String> lobbyServers, int updateIntervalSeconds, int statusIntervalSeconds,
                    Set<String> allowedCommands, Map<String, List<String>> groups,
                    Map<String, ServerPicker.Strategy> strategies, String joinFullPermission) {

    /** Group that lists every lobby; added automatically. */
    static final String LOBBIES_GROUP = "lobbies";
    /** Used when config.toml sets no permission. */
    static final String DEFAULT_JOIN_FULL_PERMISSION = "lobby.bridge.join-full";

    private static final String FILE_NAME = "config.toml";
    private static final int DEFAULT_UPDATE_INTERVAL = 2;
    private static final int DEFAULT_STATUS_INTERVAL = 10;

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
        Set<String> lobbies = new LinkedHashSet<>(toml.getList("lobby-servers", List.of("lobby")));
        int update = interval(toml, logger, "update-interval", DEFAULT_UPDATE_INTERVAL, 1, 60);
        int status = interval(toml, logger, "status-interval", DEFAULT_STATUS_INTERVAL, 5, 300);

        Set<String> allowed = new LinkedHashSet<>();
        for (Object command : toml.getList("allowed-commands", List.<Object>of("mute", "tempmute", "warn"))) {
            String word = String.valueOf(command).trim().toLowerCase(Locale.ROOT);
            if (word.startsWith("/")) {
                word = word.substring(1);
            }
            if (!word.isEmpty() && !word.contains(" ")) {
                allowed.add(word);
            } else {
                logger.warn("config.toml: 'allowed-commands' entries must be single words, skipped: {}", command);
            }
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
        groups.putIfAbsent(LOBBIES_GROUP, List.copyOf(lobbies));

        Map<String, ServerPicker.Strategy> strategies = new LinkedHashMap<>();
        Toml strategyTable = toml.getTable("group-strategies");
        if (strategyTable != null) {
            for (Map.Entry<String, Object> entry : strategyTable.entrySet()) {
                ServerPicker.Strategy strategy = ServerPicker.Strategy.fromName(String.valueOf(entry.getValue()));
                if (strategy == null) {
                    logger.warn("config.toml: group-strategies '{}' must be \"least-players\" or \"fill-first\","
                            + " got '{}'. Using {}.", entry.getKey(), entry.getValue(),
                            ServerPicker.Strategy.DEFAULT.configName());
                } else if (!groups.containsKey(entry.getKey())) {
                    logger.warn("config.toml: group-strategies names '{}', which is not a group. Ignored.",
                            entry.getKey());
                } else {
                    strategies.put(entry.getKey(), strategy);
                }
            }
        }
        String joinFull = toml.getString("join-full-permission", DEFAULT_JOIN_FULL_PERMISSION).trim();
        return new BridgeConfig(Set.copyOf(lobbies), update, status, Set.copyOf(allowed), groups,
                Map.copyOf(strategies), joinFull);
    }

    /** How {@code group} picks its server. */
    ServerPicker.Strategy strategyOf(String group) {
        return strategies.getOrDefault(group, ServerPicker.Strategy.DEFAULT);
    }

    private static int interval(Toml toml, Logger logger, String key, int fallback, int min, int max) {
        long value = toml.getLong(key, (long) fallback);
        if (value < min || value > max) {
            logger.warn("config.toml: '{}' must be from {} to {}, got {}. Using {}.", key, min, max, value, fallback);
            return fallback;
        }
        return (int) value;
    }
}
