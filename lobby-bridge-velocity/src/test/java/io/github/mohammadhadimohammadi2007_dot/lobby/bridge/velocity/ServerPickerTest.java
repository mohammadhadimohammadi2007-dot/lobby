package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity.ServerPicker.ServerState;
import io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity.ServerPicker.Strategy;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage.ConnectResult.Outcome;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerPickerTest {

    private static final List<String> GROUP = List.of("bw-1", "bw-2", "bw-3");

    private static Map<String, ServerState> states(ServerState... entries) {
        Map<String, ServerState> states = new LinkedHashMap<>();
        for (ServerState state : entries) {
            states.put(state.name(), state);
        }
        return states;
    }

    @Test
    void leastPlayersPicksTheEmptiestServer() {
        Map<String, ServerState> states = states(
                new ServerState("bw-1", 8, 16, true),
                new ServerState("bw-2", 2, 16, true),
                new ServerState("bw-3", 5, 16, true));

        assertEquals("bw-2", ServerPicker.pick(GROUP, states, Strategy.LEAST_PLAYERS, false).server());
    }

    @Test
    void fillFirstPicksTheFirstWithRoom() {
        Map<String, ServerState> states = states(
                new ServerState("bw-1", 16, 16, true),
                new ServerState("bw-2", 2, 16, true),
                new ServerState("bw-3", 0, 16, true));

        assertEquals("bw-2", ServerPicker.pick(GROUP, states, Strategy.FILL_FIRST, false).server());
    }

    @Test
    void offlineServersAreSkipped() {
        Map<String, ServerState> states = states(
                new ServerState("bw-1", 0, 16, false),
                new ServerState("bw-2", 9, 16, true));

        // bw-1 looks emptiest but did not answer the last ping; bw-3 is not known at all.
        assertEquals("bw-2", ServerPicker.pick(GROUP, states, Strategy.LEAST_PLAYERS, false).server());
    }

    @Test
    void everyServerOfflineReportsOffline() {
        Map<String, ServerState> states = states(
                new ServerState("bw-1", 0, 16, false),
                new ServerState("bw-2", 0, 16, false));

        ServerPicker.Choice choice = ServerPicker.pick(GROUP, states, Strategy.LEAST_PLAYERS, false);
        assertNull(choice.server());
        assertEquals(Outcome.OFFLINE, choice.outcome());
    }

    @Test
    void everyServerFullReportsFullUnlessAllowed() {
        Map<String, ServerState> states = states(
                new ServerState("bw-1", 16, 16, true),
                new ServerState("bw-2", 20, 16, true));

        ServerPicker.Choice refused = ServerPicker.pick(GROUP, states, Strategy.LEAST_PLAYERS, false);
        assertNull(refused.server());
        assertEquals(Outcome.FULL, refused.outcome());

        // Staff with the permission may join anyway, and still get the emptiest one.
        assertEquals("bw-1", ServerPicker.pick(GROUP, states, Strategy.LEAST_PLAYERS, true).server());
    }

    @Test
    void unknownPlayerLimitIsNeverFull() {
        Map<String, ServerState> states = states(new ServerState("bw-1", 500, 0, true));

        assertFalse(ServerPicker.isFull(new ServerState("bw-1", 500, 0, true)));
        assertEquals("bw-1", ServerPicker.pick(List.of("bw-1"), states, Strategy.FILL_FIRST, false).server());
    }

    @Test
    void emptyGroupIsUnknown() {
        ServerPicker.Choice choice = ServerPicker.pick(List.of(), Map.of(), Strategy.LEAST_PLAYERS, false);
        assertEquals(Outcome.UNKNOWN, choice.outcome());
    }

    @Test
    void strategyNamesFromConfig() {
        assertEquals(Strategy.LEAST_PLAYERS, Strategy.fromName("least-players"));
        assertEquals(Strategy.LEAST_PLAYERS, Strategy.fromName(" LEAST_PLAYERS "));
        assertEquals(Strategy.FILL_FIRST, Strategy.fromName("fill-first"));
        assertNull(Strategy.fromName("random"));
        assertEquals("least-players", Strategy.LEAST_PLAYERS.configName());
        assertEquals("fill-first", Strategy.FILL_FIRST.configName());
        assertTrue(Strategy.DEFAULT == Strategy.LEAST_PLAYERS);
    }
}
