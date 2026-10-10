package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

import java.util.concurrent.CompletableFuture;

/** Sends players to other servers: through the bridge, BungeeCord's channel, or not at all (standalone). */
public interface Connector {

    /**
     * Sends {@code player} to the server named {@code server} on the proxy.
     *
     * @return completes with true if the player is on their way, false if it failed (they were told why).
     *         Behind BungeeCord the lobby never hears back, so it is always true there
     */
    CompletableFuture<Boolean> connect(Player player, String server);

    /** Sends {@code player} to the best server of {@code group} (the proxy chooses); see {@link #connect}. */
    CompletableFuture<Boolean> connectGroup(Player player, String group);
}
