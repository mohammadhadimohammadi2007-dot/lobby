package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import net.minestom.server.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;

/** Turns the connection settings from config.yml into Minestom's {@link Auth} and logs safety warnings. */
public final class AuthFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthFactory.class);

    private AuthFactory() {
    }

    /** Creates the authentication mode for the configured connection mode. */
    public static Auth create(LobbyConfig.Connection connection) {
        return switch (connection.mode()) {
            case STANDALONE -> connection.onlineMode() ? new Auth.Online() : new Auth.Offline();
            case VELOCITY -> new Auth.Velocity(connection.velocitySecret());
            // Minestom rejects an empty token set; null means "BungeeGuard off".
            case BUNGEECORD -> new Auth.Bungee(connection.bungeeGuardTokens().isEmpty()
                    ? null
                    : new LinkedHashSet<>(connection.bungeeGuardTokens()));
        };
    }

    /** Short human description of the mode, used in logs and /lobby info. */
    public static String describe(LobbyConfig.Connection connection) {
        return switch (connection.mode()) {
            case STANDALONE -> connection.onlineMode() ? "standalone (online mode)" : "standalone (offline mode)";
            case VELOCITY -> "velocity (modern forwarding)";
            case BUNGEECORD -> connection.bungeeGuardTokens().isEmpty()
                    ? "bungeecord (legacy forwarding)"
                    : "bungeecord (legacy forwarding + BungeeGuard)";
        };
    }

    /** Logs warnings about settings that are unsafe if the port is reachable from the internet. */
    public static void logSafetyWarnings(LobbyConfig.Connection connection, int port) {
        switch (connection.mode()) {
            case STANDALONE -> {
                if (!connection.onlineMode()) {
                    LOGGER.warn("Offline mode is ON: anyone can join with any username, including staff names.");
                    LOGGER.warn("That is fine for testing. If port {} is reachable from the internet,"
                            + " set online-mode: true or put the server behind a proxy.", port);
                }
            }
            case VELOCITY -> LOGGER.info("Accepting players only through Velocity (secret is set).");
            case BUNGEECORD -> {
                LOGGER.warn("BungeeCord legacy forwarding trusts whoever connects to port {}.", port);
                LOGGER.warn("Firewall this port so only your proxy can reach it, or anyone can join as any player.");
                if (connection.bungeeGuardTokens().isEmpty()) {
                    LOGGER.warn("Tip: install BungeeGuard on the proxy and set bungeeguard-tokens for extra protection.");
                }
            }
        }
    }
}
