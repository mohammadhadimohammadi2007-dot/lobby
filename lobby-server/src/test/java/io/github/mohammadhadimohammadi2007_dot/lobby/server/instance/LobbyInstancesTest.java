package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many instances the {@code lobbies:} settings ask for, and how the join strategy is written. */
class LobbyInstancesTest {

    private static LobbyConfig.Lobbies lobbies(int instances, int perInstance) {
        return new LobbyConfig.Lobbies(instances, perInstance, JoinStrategy.LEAST_PLAYERS);
    }

    @Test
    void aFixedNumberIsUsedAsWritten() {
        assertFalse(lobbies(3, 50).auto());
        assertEquals(3, lobbies(3, 50).instanceCount(200));
        // The number does not depend on max-players.
        assertEquals(3, lobbies(3, 50).instanceCount(10));
        assertEquals(1, lobbies(1, 50).instanceCount(1000));
    }

    @Test
    void autoFollowsMaxPlayers() {
        assertTrue(lobbies(LobbyConfig.Lobbies.AUTO, 50).auto());
        assertEquals(4, lobbies(LobbyConfig.Lobbies.AUTO, 50).instanceCount(200));
        // A remainder needs its own instance, and there is always at least one.
        assertEquals(5, lobbies(LobbyConfig.Lobbies.AUTO, 50).instanceCount(201));
        assertEquals(1, lobbies(LobbyConfig.Lobbies.AUTO, 50).instanceCount(1));
        // Never more instances than a selector menu can show.
        assertEquals(LobbyConfig.MAX_INSTANCES, lobbies(LobbyConfig.Lobbies.AUTO, 1).instanceCount(10_000));
    }

    @Test
    void joinStrategyNamesMatchTheConfigFile() {
        assertEquals("least-players", JoinStrategy.LEAST_PLAYERS.configName());
        assertEquals("fill-first", JoinStrategy.FILL_FIRST.configName());
        assertEquals(JoinStrategy.configNames(), java.util.Set.of("least-players", "fill-first"));
        assertEquals(JoinStrategy.FILL_FIRST, JoinStrategy.fromConfigName("fill-first"));
    }
}
