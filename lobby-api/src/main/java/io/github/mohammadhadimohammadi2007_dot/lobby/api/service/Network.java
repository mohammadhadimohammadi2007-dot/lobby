package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The network as the proxy bridge reports it (on Velocity with lobby-bridge.jar). Without the bridge every
 * count is 0 and nothing is online. Reads are from memory; any thread.
 */
public interface Network {

    /** How a group of servers is doing. */
    enum GroupStatus {
        /** At least one server is up and has room. */
        ONLINE,
        /** Every server that is up is full. */
        FULL,
        /** No server of the group is up, or the group is unknown. */
        OFFLINE
    }

    /** True if the bridge has reported at least once. */
    boolean available();

    /** Players on the whole network. */
    int totalOnline();

    /** Players on one server. */
    int online(String server);

    /** Players on every server of a group. */
    int groupOnline(String group);

    boolean serverOnline(String server);

    GroupStatus groupStatus(String group);

    /** Every server name. */
    Set<String> servers();

    /** Every group with its servers. */
    Map<String, List<String>> groups();

    /**
     * Sends the player to a server, telling them if it fails (full, offline...), as the {@code connect} action
     * does. Completes with true if they are on their way. In standalone mode it says that this is not
     * available and completes with false.
     */
    CompletableFuture<Boolean> connect(Player player, String server);

    /** Sends the player to the best server of a group, chosen by the proxy; see {@link #connect}. */
    CompletableFuture<Boolean> connectGroup(Player player, String group);
}
