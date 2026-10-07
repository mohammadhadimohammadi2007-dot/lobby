package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The latest network snapshot from the proxy bridge: player counts per server and server groups.
 * Before any snapshot arrives (or without a bridge) every count is 0.
 * Thread-safe and cheap; later phases read it from scoreboards and menus.
 */
public final class NetworkState {

    private volatile BridgeMessage.NetworkSnapshot snapshot = BridgeMessage.NetworkSnapshot.empty();
    private volatile long lastUpdateMillis;

    /** Replaces the snapshot. Called by the bridge listener. */
    void update(BridgeMessage.NetworkSnapshot newSnapshot) {
        snapshot = newSnapshot;
        lastUpdateMillis = System.currentTimeMillis();
    }

    /** Players online on the whole network. */
    public int totalOnline() {
        return snapshot.totalOnline();
    }

    /** Players on one backend server; 0 if unknown. */
    public int online(String serverName) {
        return snapshot.serverCounts().getOrDefault(serverName, 0);
    }

    /** Players on all servers of a group; 0 if the group is unknown. */
    public int groupOnline(String groupName) {
        return snapshot.groups().getOrDefault(groupName, List.of()).stream().mapToInt(this::online).sum();
    }

    /** Server names in a group, in the order from the bridge config. */
    public List<String> group(String groupName) {
        return snapshot.groups().getOrDefault(groupName, List.of());
    }

    /** All groups, in the order from the bridge config. */
    public Map<String, List<String>> groups() {
        return snapshot.groups();
    }

    /** When the last snapshot arrived, or empty if none has arrived yet. */
    public Optional<Long> lastUpdateMillis() {
        long last = lastUpdateMillis;
        return last == 0 ? Optional.empty() : Optional.of(last);
    }
}
