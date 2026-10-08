package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The latest network data from the proxy bridge: player counts per server, server groups and whether
 * each server is online. Before any data arrives (or without a bridge) every count is 0 and every
 * server counts as offline. Thread-safe and cheap; placeholders and menus read it.
 */
public final class NetworkState {

    private volatile BridgeMessage.NetworkSnapshot snapshot = BridgeMessage.NetworkSnapshot.empty();
    private volatile Map<String, BridgeMessage.ServerStatus.Status> statuses = Map.of();
    private volatile long lastUpdateMillis;

    /** Replaces the snapshot. Called by the bridge listener (and by tests). */
    public void update(BridgeMessage.NetworkSnapshot newSnapshot) {
        snapshot = newSnapshot;
        lastUpdateMillis = System.currentTimeMillis();
    }

    /** Replaces the server statuses. Called by the bridge listener (and by tests). */
    public void update(BridgeMessage.ServerStatus newStatus) {
        statuses = newStatus.servers();
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

    /** True if the proxy's last ping of this server succeeded. Unknown servers count as offline. */
    public boolean serverOnline(String serverName) {
        BridgeMessage.ServerStatus.Status status = statuses.get(serverName);
        return status != null && status.online();
    }

    /** The player limit the server reported, or 0 if offline or unknown. */
    public int serverMaxPlayers(String serverName) {
        BridgeMessage.ServerStatus.Status status = statuses.get(serverName);
        return status == null ? 0 : status.maxPlayers();
    }

    /** When the last snapshot arrived, or empty if none has arrived yet. */
    public Optional<Long> lastUpdateMillis() {
        long last = lastUpdateMillis;
        return last == 0 ? Optional.empty() : Optional.of(last);
    }
}
