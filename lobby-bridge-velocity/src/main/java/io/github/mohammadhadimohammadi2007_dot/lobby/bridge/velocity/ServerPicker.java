package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage.ConnectResult.Outcome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses which server of a group a player should join. Kept free of Velocity classes so it can be
 * tested on its own.
 *
 * <p>Servers that did not answer the last ping are skipped, and so are full ones unless the player may
 * join those. What "best" means is set per group in config.toml.
 */
final class ServerPicker {

    /** How a group picks its server. */
    enum Strategy {
        /** The emptiest server, so players are spread evenly. Good for lobbies and queues. */
        LEAST_PLAYERS,
        /** The first server in the group's list that still has room, so games fill up one by one. */
        FILL_FIRST;

        static final Strategy DEFAULT = LEAST_PLAYERS;

        /** Reads a name from config.toml, or {@code null} if it is not a strategy. */
        static @Nullable Strategy fromName(String name) {
            return switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "least-players", "least_players" -> LEAST_PLAYERS;
                case "fill-first", "fill_first" -> FILL_FIRST;
                default -> null;
            };
        }

        /** The name as written in config.toml. */
        String configName() {
            return this == LEAST_PLAYERS ? "least-players" : "fill-first";
        }
    }

    /**
     * What one server looks like right now.
     *
     * @param online     players connected
     * @param maxPlayers the limit it reported when pinged, 0 if unknown
     * @param reachable  true if it answered the proxy's last ping
     */
    record ServerState(String name, int online, int maxPlayers, boolean reachable) {
    }

    /**
     * The chosen server, or why none could be chosen.
     *
     * @param server  the server to send the player to, or {@code null}
     * @param outcome {@link Outcome#CONNECTED} when a server was found (it is not connected yet)
     */
    record Choice(@Nullable String server, Outcome outcome) {
        static Choice of(String server) {
            return new Choice(server, Outcome.CONNECTED);
        }

        static Choice failed(Outcome outcome) {
            return new Choice(null, outcome);
        }
    }

    private ServerPicker() {
    }

    /**
     * Picks a server of {@code candidates}.
     *
     * @param candidates server names in the group's configured order
     * @param states     what is known about each server; a name that is missing counts as offline
     * @param allowFull  true if the player may join a server that is already full
     */
    static Choice pick(List<String> candidates, Map<String, ServerState> states, Strategy strategy, boolean allowFull) {
        if (candidates.isEmpty()) {
            return Choice.failed(Outcome.UNKNOWN);
        }
        boolean anyOnline = false;
        String best = null;
        int bestOnline = Integer.MAX_VALUE;
        for (String name : candidates) {
            ServerState state = states.get(name);
            if (state == null || !state.reachable()) {
                continue;
            }
            anyOnline = true;
            if (!allowFull && isFull(state)) {
                continue;
            }
            if (strategy == Strategy.FILL_FIRST) {
                return Choice.of(name);
            }
            if (state.online() < bestOnline) {
                best = name;
                bestOnline = state.online();
            }
        }
        if (best != null) {
            return Choice.of(best);
        }
        return Choice.failed(anyOnline ? Outcome.FULL : Outcome.OFFLINE);
    }

    /** True if the server reported a player limit and has reached it. */
    static boolean isFull(ServerState state) {
        return state.maxPlayers() > 0 && state.online() >= state.maxPlayers();
    }
}
