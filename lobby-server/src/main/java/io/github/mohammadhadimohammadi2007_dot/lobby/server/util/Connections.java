package io.github.mohammadhadimohammadi2007_dot.lobby.server.util;

import net.minestom.server.network.player.PlayerConnection;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Helpers for player connections. */
public final class Connections {

    private Connections() {
    }

    /**
     * The player's IP address as text, e.g. {@code 203.0.113.7}. Behind a proxy this is the real player IP
     * forwarded by the proxy. Returns an empty string if unknown.
     */
    public static String ip(PlayerConnection connection) {
        SocketAddress address = connection.getRemoteAddress();
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return "";
    }
}
