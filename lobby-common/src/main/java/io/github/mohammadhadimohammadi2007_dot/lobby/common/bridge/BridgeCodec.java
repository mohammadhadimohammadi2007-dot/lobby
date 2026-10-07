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
 * Strings are an unsigned short length followed by that many UTF-8 bytes.
 * See {@code docs/bridge-protocol.md} for the full layout of each message.
 */
public final class BridgeCodec {

    private BridgeCodec() {
    }

    /**
     * Encodes a message.
     *
     * @param message the message to encode
     * @return the encoded bytes
     * @throws IllegalArgumentException if the message breaks a protocol limit (too long string, too many entries)
     */
    public static byte[] encode(BridgeMessage message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(BridgeProtocol.VERSION);
            out.writeByte(message.typeId());
            switch (message) {
                case BridgeMessage.ClientVersion msg -> {
                    out.writeLong(msg.playerId().getMostSignificantBits());
                    out.writeLong(msg.playerId().getLeastSignificantBits());
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
            }
        } catch (IOException e) {
            // Writing to a byte array never fails.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Decodes a message.
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
                case BridgeMessage.ClientVersion.TYPE_ID -> new BridgeMessage.ClientVersion(
                        new UUID(in.readLong(), in.readLong()), in.readInt());
                case BridgeMessage.NetworkSnapshot.TYPE_ID -> readSnapshot(in);
                default -> throw new BridgeFormatException("Unknown bridge message type " + typeId);
            };
            if (in.available() > 0) {
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
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BridgeProtocol.MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String is too long (" + bytes.length + " bytes, max "
                    + BridgeProtocol.MAX_STRING_BYTES + "): " + value);
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException, BridgeFormatException {
        int length = in.readUnsignedShort();
        if (length > BridgeProtocol.MAX_STRING_BYTES) {
            throw new BridgeFormatException("String is too long: " + length + " bytes (max "
                    + BridgeProtocol.MAX_STRING_BYTES + ")");
        }
        return new String(in.readNBytes(checkedLength(in, length)), StandardCharsets.UTF_8);
    }

    private static int checkedLength(DataInputStream in, int length) throws IOException {
        if (in.available() < length) {
            throw new IOException("String length " + length + " is longer than the remaining data");
        }
        return length;
    }
}
