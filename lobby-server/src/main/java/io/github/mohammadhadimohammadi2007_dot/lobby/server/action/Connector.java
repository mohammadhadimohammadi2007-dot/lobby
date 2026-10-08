package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

/** Sends players to other servers: through the bridge, BungeeCord's channel, or not at all (standalone). */
public interface Connector {

    /** Sends {@code player} to the server named {@code server} on the proxy. */
    void connect(Player player, String server);

    /** Sends {@code player} to the best server of {@code group} (the proxy chooses). */
    void connectGroup(Player player, String group);
}
