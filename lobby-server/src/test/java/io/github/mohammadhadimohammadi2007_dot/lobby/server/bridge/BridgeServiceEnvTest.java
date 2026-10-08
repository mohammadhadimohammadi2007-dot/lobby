package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeProtocol;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lobby side of bridge v2 with fake players: chat relay, server status and command requests. */
@EnvTest
class BridgeServiceEnvTest {

    @Test
    void withoutBridgeEverythingDegradesGracefully(Env env) {
        BridgeService bridge = new BridgeService(false);
        Player player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));

        assertFalse(bridge.available());
        assertFalse(bridge.sendChat(player, chat("hi")));
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> bridge.requestCommand("mute Steve", "test").get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("disabled", bridge.status());
    }

    @Test
    void receivesStatusAndChatAndIgnoresUnknownTypes(Env env) {
        BridgeService bridge = new BridgeService(true);
        List<BridgeMessage.ChatRelay> received = new ArrayList<>();
        bridge.onChatRelay(received::add);

        bridge.receive(BridgeCodec.encode(new BridgeMessage.ServerStatus(Map.of(
                "bw-1", new BridgeMessage.ServerStatus.Status(true, 100),
                "bw-2", new BridgeMessage.ServerStatus.Status(false, 0)))), "test");
        bridge.receive(BridgeCodec.encode(chat("سلام")), "test");
        bridge.receive(new byte[]{BridgeProtocol.VERSION, 99, 1, 2, 3}, "test");

        assertTrue(bridge.networkState().serverOnline("bw-1"));
        assertFalse(bridge.networkState().serverOnline("bw-2"));
        assertFalse(bridge.networkState().serverOnline("never-heard-of"));
        assertEquals(100, bridge.networkState().serverMaxPlayers("bw-1"));
        assertEquals(List.of(chat("سلام")), received);
    }

    @Test
    void commandRequestIsSentAndAnswered(Env env) throws Exception {
        BridgeService bridge = new BridgeService(true);
        TestConnection connection = env.createConnection();
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 42, 0));
        Collector<PluginMessagePacket> outgoing = connection.trackIncoming(PluginMessagePacket.class);

        CompletableFuture<BridgeMessage.CommandResult> result = bridge.requestCommand("tempmute Steve 10m Spam", "chat auto-mute");

        BridgeMessage sent = outgoing.collect().stream()
                .filter(packet -> packet.channel().equals(BridgeProtocol.CHANNEL))
                .map(packet -> decode(packet.data()))
                .findFirst().orElseThrow();
        BridgeMessage.CommandRequest request = assertInstanceOf(BridgeMessage.CommandRequest.class, sent);
        assertEquals("tempmute Steve 10m Spam", request.command());
        assertFalse(result.isDone());

        // The proxy answers through the same connection.
        bridge.receive(BridgeCodec.encode(new BridgeMessage.CommandResult(request.requestId(), true, "")), player.getUsername());

        assertTrue(result.get(1, TimeUnit.SECONDS).accepted());
    }

    private static BridgeMessage.ChatRelay chat(String text) {
        return new BridgeMessage.ChatRelay("m1", "global", "lobby-2", new UUID(1, 2), "Ali", "", "", "default", Map.of(), text);
    }

    private static BridgeMessage decode(byte[] data) {
        try {
            return BridgeCodec.decode(data);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
