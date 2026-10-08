package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A message on the {@link BridgeProtocol#CHANNEL} channel, between the proxy plugin and the lobbies.
 *
 * <p>Use {@link BridgeCodec} to turn a message into bytes and back. The wire format of every type is
 * documented in {@code docs/bridge-protocol.md}.
 */
public sealed interface BridgeMessage {

    /** Numeric id written on the wire to identify the message type. */
    int typeId();

    /**
     * Proxy to lobby. Tells the lobby which Minecraft protocol version a player's client really uses.
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
     * Proxy to lobby. A snapshot of the whole network, pushed regularly.
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

    /**
     * Proxy to lobby. Whether each backend server answers pings, and its player limit.
     *
     * @param servers server name to status, in the proxy's order
     */
    record ServerStatus(Map<String, Status> servers) implements BridgeMessage {
        public static final int TYPE_ID = 3;

        /**
         * @param online     true if the server answered the last ping
         * @param maxPlayers the player limit it reported, 0 if offline or unknown
         */
        public record Status(boolean online, int maxPlayers) {
        }

        public ServerStatus {
            servers = Collections.unmodifiableMap(new LinkedHashMap<>(servers));
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Lobby to proxy, then proxy to every other lobby: one chat message in a network-wide channel
     * (for example {@code global} or {@code staff}). The text is already filtered by the sending lobby;
     * receiving lobbies only render it with their own formats.
     *
     * @param messageId    short id of the message, the same on every lobby (used by staff tools)
     * @param channel      channel name, e.g. {@code global}
     * @param originServer name of the lobby the message was written on
     * @param senderId     the sender's UUID
     * @param senderName   the sender's username
     * @param prefix       the sender's rank prefix (trusted, may contain colors)
     * @param suffix       the sender's rank suffix (trusted, may contain colors)
     * @param primaryGroup the sender's primary group, used to choose the chat format
     * @param meta         extra rank meta (for example a chat color), may be empty
     * @param message      the message text as players should see it (after censoring)
     */
    record ChatRelay(
            String messageId,
            String channel,
            String originServer,
            UUID senderId,
            String senderName,
            String prefix,
            String suffix,
            String primaryGroup,
            Map<String, String> meta,
            String message
    ) implements BridgeMessage {
        public static final int TYPE_ID = 4;

        public ChatRelay {
            Objects.requireNonNull(senderId, "senderId");
            meta = Collections.unmodifiableMap(new LinkedHashMap<>(meta));
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Lobby to proxy: please run this command in the proxy console (for example a LiteBans mute after
     * repeated chat violations). The proxy only runs commands from its allowlist and only when the
     * message comes from a lobby server connection, never from a player.
     *
     * @param requestId short id, echoed in the {@link CommandResult}
     * @param command   the command without a leading slash, e.g. {@code tempmute Steve 10m Spam}
     * @param reason    why it is requested, for the proxy's log (e.g. {@code chat auto-mute})
     */
    record CommandRequest(String requestId, String command, String reason) implements BridgeMessage {
        public static final int TYPE_ID = 5;

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Proxy to lobby: the answer to a {@link CommandRequest}.
     *
     * @param requestId the id from the request
     * @param accepted  true if the proxy ran the command
     * @param detail    why it was refused, or empty
     */
    record CommandResult(String requestId, boolean accepted, String detail) implements BridgeMessage {
        public static final int TYPE_ID = 6;

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * A message type this build does not know, from a newer proxy or lobby. Readers ignore it, so
     * mixing versions does not break anything.
     *
     * @param unknownTypeId the type id that was read
     */
    record Unknown(int unknownTypeId) implements BridgeMessage {
        @Override
        public int typeId() {
            return unknownTypeId;
        }
    }
}
