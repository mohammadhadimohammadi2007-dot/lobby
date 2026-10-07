package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A message sent from the proxy to the lobby over the {@link BridgeProtocol#CHANNEL} channel.
 *
 * <p>Use {@link BridgeCodec} to turn a message into bytes and back.
 */
public sealed interface BridgeMessage {

    /** Numeric id written on the wire to identify the message type. */
    int typeId();

    /**
     * Tells the lobby which Minecraft protocol version a player's client really uses.
     * Behind ViaVersion the lobby only sees the translated version, so the proxy reports the real one.
     *
     * @param playerId        the player's UUID
     * @param protocolVersion the client's protocol version number (for example 47 for 1.8.x)
     */
    record ClientVersion(UUID playerId, int protocolVersion) implements BridgeMessage {
        public static final int TYPE_ID = 1;

        public ClientVersion {
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * A snapshot of the whole network, pushed regularly by the proxy.
     *
     * @param totalOnline  players online on the whole proxy
     * @param serverCounts players online per backend server name
     * @param groups       server groups from the bridge config, group name to server names
     */
    record NetworkSnapshot(
            int totalOnline,
            Map<String, Integer> serverCounts,
            Map<String, List<String>> groups
    ) implements BridgeMessage {
        public static final int TYPE_ID = 2;

        /** Copies both maps, keeping their order, so the snapshot can never change afterwards. */
        public NetworkSnapshot {
            serverCounts = Collections.unmodifiableMap(new LinkedHashMap<>(serverCounts));
            Map<String, List<String>> groupsCopy = new LinkedHashMap<>();
            groups.forEach((name, servers) -> groupsCopy.put(name, List.copyOf(servers)));
            groups = Collections.unmodifiableMap(groupsCopy);
        }

        /** An empty snapshot, used before the proxy has sent anything. */
        public static NetworkSnapshot empty() {
            return new NetworkSnapshot(0, Map.of(), Map.of());
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }
}
