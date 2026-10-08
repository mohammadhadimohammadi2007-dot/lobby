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
    void unknownTypeIsIgnoredNotRejected() throws BridgeFormatException {
        // A newer sender may add types; older readers must not fail on them.
        assertEquals(new BridgeMessage.Unknown(42), BridgeCodec.decode(new byte[]{1, 42, 7, 7, 7}));
    }

    @Test
    void unknownCannotBeEncoded() {
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.encode(new BridgeMessage.Unknown(42)));
    }

    @Test
    void serverStatusRoundTrip() throws BridgeFormatException {
        Map<String, BridgeMessage.ServerStatus.Status> servers = new LinkedHashMap<>();
        servers.put("bw-1", new BridgeMessage.ServerStatus.Status(true, 100));
        servers.put("bw-2", new BridgeMessage.ServerStatus.Status(false, 0));
        var original = new BridgeMessage.ServerStatus(servers);

        var decoded = assertInstanceOf(BridgeMessage.ServerStatus.class, BridgeCodec.decode(BridgeCodec.encode(original)));
        assertEquals(original, decoded);
        assertEquals(List.of("bw-1", "bw-2"), List.copyOf(decoded.servers().keySet()));
    }

    @Test
    void chatRelayRoundTripWithPersianText() throws BridgeFormatException {
        String persian = "سلام به همه! این یک پیام آزمایشی است ‌ با نیم‌فاصله و ۱۲۳";
        var original = new BridgeMessage.ChatRelay("a1b2", "global", "lobby-1", UUID.randomUUID(), "Ali",
                "&6[VIP] ", "", "vip", Map.of("chat_color", "<yellow>"), persian.repeat(10));

        assertEquals(original, BridgeCodec.decode(BridgeCodec.encode(original)));
    }

    @Test
    void chatRelayTooLongIsRejectedOnEncode() {
        var tooLong = new BridgeMessage.ChatRelay("a", "global", "lobby", UUID.randomUUID(), "x", "", "", "default",
                Map.of(), "x".repeat(BridgeProtocol.MAX_TEXT_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.encode(tooLong));
    }

    @Test
    void commandRequestAndResultRoundTrip() throws BridgeFormatException {
        var request = new BridgeMessage.CommandRequest("r1", "tempmute Steve 10m Spam (auto)", "chat auto-mute");
        var result = new BridgeMessage.CommandResult("r1", false, "command 'op' is not in allowed-commands");

        assertEquals(request, BridgeCodec.decode(BridgeCodec.encode(request)));
        assertEquals(result, BridgeCodec.decode(BridgeCodec.encode(result)));
    }

    @Test
    void connectRequestAndResultRoundTrip() throws BridgeFormatException {
        UUID player = UUID.randomUUID();
        BridgeMessage.ConnectRequest request = new BridgeMessage.ConnectRequest("abc123", player, "bedwars", true);
        assertEquals(request, BridgeCodec.decode(BridgeCodec.encode(request)));

        for (BridgeMessage.ConnectResult.Outcome outcome : BridgeMessage.ConnectResult.Outcome.values()) {
            BridgeMessage.ConnectResult result = new BridgeMessage.ConnectResult("abc123", player, outcome, "bw-2");
            assertEquals(result, BridgeCodec.decode(BridgeCodec.encode(result)));
        }
    }

    @Test
    void unknownOutcomeNumberBecomesRefused() throws BridgeFormatException {
        UUID player = UUID.randomUUID();
        byte[] data = BridgeCodec.encode(new BridgeMessage.ConnectResult("abc123", player,
                BridgeMessage.ConnectResult.Outcome.CONNECTED, ""));
        // The outcome byte sits after the version, type, request id (2 + 6) and the UUID (16 bytes).
        data[2 + 6 + 2 + 16] = 99;

        BridgeMessage.ConnectResult decoded = (BridgeMessage.ConnectResult) BridgeCodec.decode(data);
        assertEquals(BridgeMessage.ConnectResult.Outcome.REFUSED, decoded.outcome());
    }

    @Test
    void skinUpdateRoundTripWithLongTextures() throws BridgeFormatException {
        BridgeMessage.SkinUpdate update = new BridgeMessage.SkinUpdate(UUID.randomUUID(),
                "e".repeat(1000), "s".repeat(700));
        assertEquals(update, BridgeCodec.decode(BridgeCodec.encode(update)));

        BridgeMessage.SkinUpdate unsigned = new BridgeMessage.SkinUpdate(UUID.randomUUID(), "value", "");
        assertEquals(unsigned, BridgeCodec.decode(BridgeCodec.encode(unsigned)));
    }

    @Test
    void newMessageTypesKeepTheProtocolVersion() {
        // Readers reject another version, so adding types must not change it.
        assertEquals(1, BridgeProtocol.VERSION);
        byte[] encoded = BridgeCodec.encode(new BridgeMessage.SkinUpdate(
                new UUID(1, 2), "v", ""));
        assertEquals(1, encoded[0]);
        assertEquals(9, encoded[1]);
    }

    @Test
    void oldMessageLayoutsAreUnchanged() {
        // Version 1 readers from Phase 1 must still understand snapshots: same bytes as before.
        byte[] encoded = BridgeCodec.encode(new BridgeMessage.NetworkSnapshot(5, Map.of("a", 5), Map.of()));
        byte[] expected = {1, 2, 0, 0, 0, 5, 0, 1, 0, 1, 'a', 0, 0, 0, 5, 0, 0};
        assertArrayEquals(expected, encoded);
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
