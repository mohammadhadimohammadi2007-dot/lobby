package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** How players reach this server. Matches the {@code mode} option in config.yml. */
public enum ConnectionMode {
    /** Players connect directly. No proxy. */
    STANDALONE,
    /** Behind a Velocity proxy with modern forwarding. */
    VELOCITY,
    /** Behind BungeeCord or Waterfall with legacy (IP) forwarding. */
    BUNGEECORD;

    /** The value written in config.yml, e.g. {@code velocity}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for modes where a proxy sits in front of the lobby. */
    public boolean behindProxy() {
        return this != STANDALONE;
    }

    /** All values accepted in config.yml. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(ConnectionMode::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** Parses a config value that was already validated against {@link #configNames()}. */
    public static ConnectionMode fromConfigName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
