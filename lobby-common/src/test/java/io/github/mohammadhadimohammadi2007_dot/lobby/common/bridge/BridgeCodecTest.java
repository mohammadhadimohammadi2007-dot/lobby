package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeCodecTest {

    @Test
    void clientVersionRoundTrip() throws BridgeFormatException {
        var original = new BridgeMessage.ClientVersion(UUID.randomUUID(), 47);
        assertEquals(original, BridgeCodec.decode(BridgeCodec.encode(original)));
    }

    @Test
    void clientVersionHasStableLayout() {
        var uuid = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);
        byte[] expected = {
                1, 1, // version, type
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, // uuid
                0, 0, 3, 0 // protocol 768
        };
        assertArrayEquals(expected, BridgeCodec.encode(new BridgeMessage.ClientVersion(uuid, 768)));
    }

    @Test
    void snapshotRoundTripKeepsOrder() throws BridgeFormatException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("lobby-1", 120);
        counts.put("bw-2", 15);
        counts.put("bw-1", 30);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("bedwars", List.of("bw-1", "bw-2"));
        groups.put("empty", List.of());
        var original = new BridgeMessage.NetworkSnapshot(165, counts, groups);

        var decoded = assertInstanceOf(BridgeMessage.NetworkSnapshot.class,
                BridgeCodec.decode(BridgeCodec.encode(original)));

        assertEquals(original, decoded);
        assertEquals(List.of("lobby-1", "bw-2", "bw-1"), List.copyOf(decoded.serverCounts().keySet()));
    }

    @Test
    void snapshotSupportsUnicodeNames() throws BridgeFormatException {
        var original = new BridgeMessage.NetworkSnapshot(1, Map.of("سرور-۱", 1), Map.of("بازی", List.of("سرور-۱")));
        assertEquals(original, BridgeCodec.decode(BridgeCodec.encode(original)));
    }

    @Test
    void rejectsOtherProtocolVersion() {
        byte[] data = BridgeCodec.encode(new BridgeMessage.ClientVersion(UUID.randomUUID(), 47));
        data[0] = 99;
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(data));
    }

    @Test
    void rejectsUnknownType() {
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(new byte[]{1, 42}));
    }

    @Test
    void rejectsTruncatedData() {
        byte[] data = BridgeCodec.encode(NetworkSnapshotFixtures.small());
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(Arrays.copyOf(data, data.length - 1)));
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] data = BridgeCodec.encode(NetworkSnapshotFixtures.small());
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(Arrays.copyOf(data, data.length + 1)));
    }

    @Test
    void rejectsHugeEntryCount() {
        // version 1, snapshot, total 0, then a server count of 65535
        byte[] data = {1, 2, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(data));
    }

    @Test
    void rejectsEmptyData() {
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(new byte[0]));
    }

    @Test
    void encodeRejectsTooLongName() {
        String longName = "x".repeat(BridgeProtocol.MAX_STRING_BYTES + 1);
        var message = new BridgeMessage.NetworkSnapshot(0, Map.of(longName, 1), Map.of());
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.encode(message));
    }

    /** Small reusable snapshot for malformed-data tests. */
    private static final class NetworkSnapshotFixtures {
        static BridgeMessage.NetworkSnapshot small() {
            return new BridgeMessage.NetworkSnapshot(3, Map.of("lobby", 3), Map.of("lobbies", List.of("lobby")));
        }
    }
}
