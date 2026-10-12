package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Network;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** The network state from the bridge, and server switching through the lobby's connector. */
final class ApiNetwork implements Network {

    private final BridgeService bridge;
    private final ActionServices actions;

    ApiNetwork(BridgeService bridge, ActionServices actions) {
        this.bridge = bridge;
        this.actions = actions;
    }

    private NetworkState state() {
        return bridge.networkState();
    }

    @Override
    public boolean available() {
        return state().hasSnapshot();
    }

    @Override
    public int totalOnline() {
        return state().totalOnline();
    }

    @Override
    public int online(String server) {
        return state().online(server);
    }

    @Override
    public int groupOnline(String group) {
        return state().groupOnline(group);
    }

    @Override
    public boolean serverOnline(String server) {
        return state().serverOnline(server);
    }

    @Override
    public GroupStatus groupStatus(String group) {
        return switch (state().groupStatus(group)) {
            case ONLINE -> GroupStatus.ONLINE;
            case FULL -> GroupStatus.FULL;
            case OFFLINE -> GroupStatus.OFFLINE;
        };
    }

    @Override
    public Set<String> servers() {
        return Set.copyOf(state().servers());
    }

    @Override
    public Map<String, List<String>> groups() {
        return Map.copyOf(state().groups());
    }

    @Override
    public CompletableFuture<Boolean> connect(Player player, String server) {
        return actions.connector().connect(player, server);
    }

    @Override
    public CompletableFuture<Boolean> connectGroup(Player player, String group) {
        return actions.connector().connectGroup(player, group);
    }
}
