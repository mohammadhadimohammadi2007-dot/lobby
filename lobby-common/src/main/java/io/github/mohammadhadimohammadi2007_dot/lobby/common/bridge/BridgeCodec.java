package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts {@link BridgeMessage}s to bytes and back.
 *
 * <p>Every message starts with a header of two unsigned bytes: the protocol version
 * ({@link BridgeProtocol#VERSION}) and the message type id. All numbers are big-endian.
 * A {@code string} is an unsigned short length followed by that many UTF-8 bytes (at most
 * {@link BridgeProtocol#MAX_STRING_BYTES}); a {@code text} is the same with a higher limit
 * ({@link BridgeProtocol#MAX_TEXT_BYTES}). See {@code docs/bridge-protocol.md} for every message's layout.
 */
public final class BridgeCodec {

    private BridgeCodec() {
    }

    /**
     * Encodes a message.
     *
     * @param message the message to encode
     * @return the encoded bytes
     * @throws IllegalArgumentException if the message breaks a protocol limit or is {@link BridgeMessage.Unknown}
     */
    public static byte[] encode(BridgeMessage message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(BridgeProtocol.VERSION);
            out.writeByte(message.typeId());
            switch (message) {
                case BridgeMessage.ClientVersion msg -> {
                    writeUuid(out, msg.playerId());
                    out.writeInt(msg.protocolVersion());
                }
                case BridgeMessage.NetworkSnapshot msg -> {
                    out.writeInt(msg.totalOnline());
                    writeCount(out, msg.serverCounts().size());
                    for (Map.Entry<String, Integer> entry : msg.serverCounts().entrySet()) {
                        writeString(out, entry.getKey());
                        out.writeInt(entry.getValue());
                    }
                    writeCount(out, msg.groups().size());
                    for (Map.Entry<String, List<String>> entry : msg.groups().entrySet()) {
                        writeString(out, entry.getKey());
                        writeCount(out, entry.getValue().size());
                        for (String server : entry.getValue()) {
                            writeString(out, server);
                        }
                    }
                }
                case BridgeMessage.ServerStatus msg -> {
                    writeCount(out, msg.servers().size());
                    for (Map.Entry<String, BridgeMessage.ServerStatus.Status> entry : msg.servers().entrySet()) {
                        writeString(out, entry.getKey());
                        out.writeBoolean(entry.getValue().online());
                        out.writeInt(entry.getValue().maxPlayers());
                    }
                }
                case BridgeMessage.ChatRelay msg -> {
                    writeString(out, msg.messageId());
                    writeString(out, msg.channel());
                    writeString(out, msg.originServer());
                    writeUuid(out, msg.senderId());
                    writeString(out, msg.senderName());
                    writeText(out, msg.prefix());
                    writeText(out, msg.suffix());
                    writeString(out, msg.primaryGroup());
                    writeCount(out, msg.meta().size());
                    for (Map.Entry<String, String> entry : msg.meta().entrySet()) {
                        writeString(out, entry.getKey());
                        writeText(out, entry.getValue());
                    }
                    writeText(out, msg.message());
                }
                case BridgeMessage.CommandRequest msg -> {
                    writeString(out, msg.requestId());
                    writeText(out, msg.command());
                    writeString(out, msg.reason());
                }
                case BridgeMessage.CommandResult msg -> {
                    writeString(out, msg.requestId());
                    out.writeBoolean(msg.accepted());
                    writeText(out, msg.detail());
                }
                case BridgeMessage.Unknown msg ->
                        throw new IllegalArgumentException("Cannot encode unknown message type " + msg.unknownTypeId());
            }
        } catch (IOException e) {
            // Writing to a byte array never fails.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Decodes a message. A type id this build does not know gives {@link BridgeMessage.Unknown}, so newer
     * senders can add message types without breaking older readers.
     *
     * @param data the raw bytes received on the bridge channel
     * @return the decoded message
     * @throws BridgeFormatException if the bytes are malformed, too large, or use another protocol version
     */
    public static BridgeMessage decode(byte[] data) throws BridgeFormatException {
        if (data.length > BridgeProtocol.MAX_MESSAGE_BYTES) {
            throw new BridgeFormatException("Message is too large: " + data.length + " bytes");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readUnsignedByte();
            if (version != BridgeProtocol.VERSION) {
                throw new BridgeFormatException("Unsupported bridge protocol version " + version
                        + " (this build understands version " + BridgeProtocol.VERSION
                        + "). Update the lobby server and the bridge plugin to the same release.");
            }
            int typeId = in.readUnsignedByte();
            BridgeMessage message = switch (typeId) {
                case BridgeMessage.ClientVersion.TYPE_ID -> new BridgeMessage.ClientVersion(readUuid(in), in.readInt());
                case BridgeMessage.NetworkSnapshot.TYPE_ID -> readSnapshot(in);
                case BridgeMessage.ServerStatus.TYPE_ID -> readServerStatus(in);
                case BridgeMessage.ChatRelay.TYPE_ID -> readChatRelay(in);
                case BridgeMessage.CommandRequest.TYPE_ID ->
                        new BridgeMessage.CommandRequest(readString(in), readText(in), readString(in));
                case BridgeMessage.CommandResult.TYPE_ID ->
                        new BridgeMessage.CommandResult(readString(in), in.readBoolean(), readText(in));
                default -> new BridgeMessage.Unknown(typeId);
            };
            if (!(message instanceof BridgeMessage.Unknown) && in.available() > 0) {
                throw new BridgeFormatException("Unexpected " + in.available() + " extra bytes after message");
            }
            return message;
        } catch (IOException e) {
            throw new BridgeFormatException("Message ended too early", e);
        }
    }

    private static BridgeMessage.NetworkSnapshot readSnapshot(DataInputStream in)
            throws IOException, BridgeFormatException {
        int totalOnline = in.readInt();
        int serverCount = readCount(in);
        Map<String, Integer> serverCounts = new LinkedHashMap<>();
        for (int i = 0; i < serverCount; i++) {
            serverCounts.put(readString(in), in.readInt());
        }
        int groupCount = readCount(in);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (int i = 0; i < groupCount; i++) {
            String name = readString(in);
            int size = readCount(in);
            List<String> servers = new ArrayList<>(size);
            for (int j = 0; j < size; j++) {
                servers.add(readString(in));
            }
            groups.put(name, servers);
        }
        return new BridgeMessage.NetworkSnapshot(totalOnline, serverCounts, groups);
    }

    private static BridgeMessage.ServerStatus readServerStatus(DataInputStream in)
            throws IOException, BridgeFormatException {
        int count = readCount(in);
        Map<String, BridgeMessage.ServerStatus.Status> servers = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            servers.put(readString(in), new BridgeMessage.ServerStatus.Status(in.readBoolean(), in.readInt()));
        }
        return new BridgeMessage.ServerStatus(servers);
    }

    private static BridgeMessage.ChatRelay readChatRelay(DataInputStream in) throws IOException, BridgeFormatException {
        String messageId = readString(in);
        String channel = readString(in);
        String origin = readString(in);
        UUID senderId = readUuid(in);
        String senderName = readString(in);
        String prefix = readText(in);
        String suffix = readText(in);
        String primaryGroup = readString(in);
        int metaCount = readCount(in);
        Map<String, String> meta = new LinkedHashMap<>();
        for (int i = 0; i < metaCount; i++) {
            meta.put(readString(in), readText(in));
        }
        String message = readText(in);
        return new BridgeMessage.ChatRelay(messageId, channel, origin, senderId, senderName, prefix, suffix,
                primaryGroup, meta, message);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeCount(DataOutputStream out, int count) throws IOException {
        if (count > BridgeProtocol.MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many entries: " + count + " (max " + BridgeProtocol.MAX_ENTRIES + ")");
        }
        out.writeShort(count);
    }

    private static int readCount(DataInputStream in) throws IOException, BridgeFormatException {
        int count = in.readUnsignedShort();
        if (count > BridgeProtocol.MAX_ENTRIES) {
            throw new BridgeFormatException("Too many entries: " + count + " (max " + BridgeProtocol.MAX_ENTRIES + ")");
        }
        return count;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        writeBytes(out, value, BridgeProtocol.MAX_STRING_BYTES);
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        writeBytes(out, value, BridgeProtocol.MAX_TEXT_BYTES);
    }

    private static void writeBytes(DataOutputStream out, String value, int limit) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) {
            throw new IllegalArgumentException("Text is too long (" + bytes.length + " bytes, max " + limit + "): "
                    + value.substring(0, Math.min(value.length(), 40)) + "...");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException, BridgeFormatException {
        return readBytes(in, BridgeProtocol.MAX_STRING_BYTES);
    }

    private static String readText(DataInputStream in) throws IOException, BridgeFormatException {
        return readBytes(in, BridgeProtocol.MAX_TEXT_BYTES);
    }

    private static String readBytes(DataInputStream in, int limit) throws IOException, BridgeFormatException {
        int length = in.readUnsignedShort();
        if (length > limit) {
            throw new BridgeFormatException("Text is too long: " + length + " bytes (max " + limit + ")");
        }
        if (in.available() < length) {
            throw new IOException("Text length " + length + " is longer than the remaining data");
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
