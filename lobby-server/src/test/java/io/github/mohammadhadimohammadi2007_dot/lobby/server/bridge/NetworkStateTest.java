package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStateTest {

    @Test
    void emptyStateReturnsZeroes() {
        NetworkState state = new NetworkState();

        assertEquals(0, state.totalOnline());
        assertEquals(0, state.online("bw-1"));
        assertEquals(0, state.groupOnline("bedwars"));
        assertTrue(state.lastUpdateMillis().isEmpty());
    }

    @Test
    void groupCountIsSumOfItsServers() {
        NetworkState state = new NetworkState();
        state.update(new BridgeMessage.NetworkSnapshot(50,
                Map.of("lobby", 20, "bw-1", 12, "bw-2", 18),
                Map.of("bedwars", List.of("bw-1", "bw-2", "bw-offline"))));

        assertEquals(50, state.totalOnline());
        assertEquals(30, state.groupOnline("bedwars"));
        assertEquals(List.of("bw-1", "bw-2", "bw-offline"), state.group("bedwars"));
        assertTrue(state.lastUpdateMillis().isPresent());
    }

    @Test
    void clientTierSplitsAt1194() {
        assertEquals(ClientCapabilities.Tier.LEGACY, new ClientCapabilities(ProtocolVersions.V1_8, true).tier());
        assertEquals(ClientCapabilities.Tier.LEGACY, new ClientCapabilities(ProtocolVersions.V1_19_4 - 1, true).tier());
        assertEquals(ClientCapabilities.Tier.MODERN, new ClientCapabilities(ProtocolVersions.V1_19_4, true).tier());
    }
}
