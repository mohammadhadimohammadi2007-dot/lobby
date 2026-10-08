package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Which lobby instance a joining player is put in. Matches {@code lobbies.join} in config.yml. */
public enum JoinStrategy {
    /**
     * The instance with the fewest players, so everyone has room. With one limit for every instance
     * this is also the instance with the most free space.
     */
    LEAST_PLAYERS,
    /** The first instance that still has room, so the first lobby fills up before the next is used. */
    FILL_FIRST;

    /** The value written in config.yml, e.g. {@code least-players}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** All values accepted in config.yml. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(JoinStrategy::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** Parses a config value that was already validated against {@link #configNames()}. */
    public static JoinStrategy fromConfigName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
