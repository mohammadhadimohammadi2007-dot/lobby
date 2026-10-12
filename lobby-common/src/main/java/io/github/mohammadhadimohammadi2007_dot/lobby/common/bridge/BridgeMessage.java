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
     * Deprecated and unused: lobbies no longer relay chat (network-wide chat belongs to the proxy). The
     * type id stays reserved and the message still decodes, so an older lobby or bridge sending it is
     * ignored quietly instead of being reported as a bad message.
     *
     * <p>Was: lobby to proxy, then proxy to every other lobby, one chat message in a network-wide channel.
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
    @Deprecated
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
     * Lobby to proxy: please send this player to another server. The lobby cannot move players itself,
     * only the proxy can. The proxy answers with a {@link ConnectResult} so the lobby can tell the player
     * what happened.
     *
     * @param requestId short id, echoed in the result
     * @param playerId  the player to move
     * @param target    a server name, or a group name if {@code group} is true
     * @param group     true to let the proxy pick the best server of the group
     */
    record ConnectRequest(String requestId, UUID playerId, String target, boolean group) implements BridgeMessage {
        public static final int TYPE_ID = 7;

        public ConnectRequest {
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Proxy to lobby: the answer to a {@link ConnectRequest}.
     *
     * @param requestId the id from the request
     * @param playerId  the player the request was about
     * @param outcome   what happened
     * @param server    the server that was chosen, or empty if none was
     */
    record ConnectResult(String requestId, UUID playerId, Outcome outcome, String server) implements BridgeMessage {
        public static final int TYPE_ID = 8;

        /** What came of a connect request. */
        public enum Outcome {
            /** The player is on their way. */
            CONNECTED(0),
            /** Every server was full (and the player may not join full servers). */
            FULL(1),
            /** The server, or every server of the group, did not answer the proxy's last ping. */
            OFFLINE(2),
            /** No server or group with that name exists on the proxy. */
            UNKNOWN(3),
            /** The proxy or another plugin refused the move, or the player left meanwhile. */
            REFUSED(4);

            private final int id;

            Outcome(int id) {
                this.id = id;
            }

            /** The number written on the wire. */
            public int id() {
                return id;
            }

            /** The outcome with this wire id, or {@link #REFUSED} for a number this build does not know. */
            public static Outcome fromId(int id) {
                for (Outcome outcome : values()) {
                    if (outcome.id == id) {
                        return outcome;
                    }
                }
                return REFUSED;
            }
        }

        public ConnectResult {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(outcome, "outcome");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Proxy to lobby: this player's skin changed (for example SkinsRestorer ran {@code /skin} on the
     * proxy). The lobby shows the new skin right away, without the player reconnecting.
     *
     * @param playerId  the player whose skin changed
     * @param value     the base64 texture value from Mojang's session server
     * @param signature Mojang's signature for {@code value}, or empty if it is unsigned
     */
    record SkinUpdate(UUID playerId, String value, String signature) implements BridgeMessage {
        public static final int TYPE_ID = 9;

        public SkinUpdate {
            Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Proxy to lobby, with every player who arrives: what the proxy is, so {@code /setup-lobby setup} and
     * {@code /setup-lobby doctor} can say what is missing there. Added in bridge 1.4; older lobbies ignore it.
     *
     * @param bridgeVersion the lobby-bridge plugin's version
     * @param proxyVersion  the proxy software and version, e.g. "Velocity 3.5.1"
     * @param onlineMode    true if the proxy checks names with Mojang
     * @param viaVersion    true if ViaVersion is installed on the proxy (needed to detect clients below 1.13)
     * @param viaBackwards  true if ViaBackwards is installed (older clients down to 1.9)
     * @param viaRewind     true if ViaRewind is installed (1.8 clients)
     */
    record ProxyInfo(String bridgeVersion, String proxyVersion, boolean onlineMode, boolean viaVersion,
                     boolean viaBackwards, boolean viaRewind) implements BridgeMessage {
        public static final int TYPE_ID = 10;

        public ProxyInfo {
            Objects.requireNonNull(bridgeVersion, "bridgeVersion");
            Objects.requireNonNull(proxyVersion, "proxyVersion");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Lobby to proxy: run this command on the proxy as this player, with the player's own permissions there
     * (for example {@code server bedwars} or a queue plugin's {@code queue bedwars}). Only sent for actions an
     * admin configured (NPCs, menus, hotbar items, portals), never for text a player typed. The proxy answers
     * with a {@link PlayerCommandResult}. Added in bridge 0.5; older bridges ignore it.
     *
     * @param requestId short id, echoed in the result
     * @param playerId  the player to run it as; must be on the lobby that sent the request
     * @param command   the command without the leading {@code /}
     */
    record PlayerCommandRequest(String requestId, UUID playerId, String command) implements BridgeMessage {
        public static final int TYPE_ID = 11;

        public PlayerCommandRequest {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(command, "command");
        }

        @Override
        public int typeId() {
            return TYPE_ID;
        }
    }

    /**
     * Proxy to lobby: the answer to a {@link PlayerCommandRequest}.
     *
     * @param requestId the id from the request
     * @param playerId  the player the request was about
     * @param outcome   what happened
     */
    record PlayerCommandResult(String requestId, UUID playerId, Outcome outcome) implements BridgeMessage {
        public static final int TYPE_ID = 12;

        /** What came of a player command request. */
        public enum Outcome {
            /** The proxy ran the command. */
            EXECUTED(0),
            /** The proxy has no such command, or the player may not use it. */
            NOT_FOUND(1),
            /** Not run: player commands are turned off on the proxy, the player is not on that lobby, or it failed. */
            FAILED(2);

            private final int id;

            Outcome(int id) {
                this.id = id;
            }

            /** The number written on the wire. */
            public int id() {
                return id;
            }

            /** The outcome with this wire id, or {@link #FAILED} for a number this build does not know. */
            public static Outcome fromId(int id) {
                for (Outcome outcome : values()) {
                    if (outcome.id == id) {
                        return outcome;
                    }
                }
                return FAILED;
            }
        }

        public PlayerCommandResult {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(outcome, "outcome");
        }

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
