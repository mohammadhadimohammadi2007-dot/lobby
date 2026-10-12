package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeProtocol;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
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

/** The lobby side of bridge v2 with fake players: server status and command requests. */
@EnvTest
class BridgeServiceEnvTest {

    @TempDir
    Path dir;

    @Test
    void withoutBridgeEverythingDegradesGracefully(Env env) {
        BridgeService bridge = new BridgeService(false);
        Player player = env.createPlayer(env.createFlatInstance(), new Pos(0, 42, 0));

        assertFalse(bridge.available());
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> bridge.requestCommand("mute Steve", "test").get(1, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("disabled", bridge.status());
    }

    @Test
    void receivesStatusAndIgnoresRelayedChatAndUnknownTypes(Env env) {
        BridgeService bridge = new BridgeService(true);

        bridge.receive(BridgeCodec.encode(new BridgeMessage.ServerStatus(Map.of(
                "bw-1", new BridgeMessage.ServerStatus.Status(true, 100),
                "bw-2", new BridgeMessage.ServerStatus.Status(false, 0)))), "test");
        // An older lobby may still relay chat: it is read and ignored.
        bridge.receive(BridgeCodec.encode(chat("hello")), "test");
        bridge.receive(new byte[]{BridgeProtocol.VERSION, 99, 1, 2, 3}, "test");

        assertTrue(bridge.networkState().serverOnline("bw-1"));
        assertFalse(bridge.networkState().serverOnline("bw-2"));
        assertFalse(bridge.networkState().serverOnline("never-heard-of"));
        assertEquals(100, bridge.networkState().serverMaxPlayers("bw-1"));
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

    @Test
    void playerCommandsAreSentAsThePlayerAndTheAnswerIsShown(Env env) throws Exception {
        BridgeService bridge = new BridgeService(true);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 42, 0));
        Collector<PluginMessagePacket> outgoing = connection.trackIncoming(PluginMessagePacket.class);
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        BridgeProxyCommands commands = new BridgeProxyCommands(bridge, text());

        CompletableFuture<Boolean> ran = commands.run(player, "queue bedwars", "NPC 'bedwars'");

        BridgeMessage.PlayerCommandRequest request = assertInstanceOf(BridgeMessage.PlayerCommandRequest.class,
                outgoing.collect().stream().filter(packet -> packet.channel().equals(BridgeProtocol.CHANNEL))
                        .map(packet -> decode(packet.data())).findFirst().orElseThrow());
        assertEquals(player.getUuid(), request.playerId());
        assertEquals("queue bedwars", request.command());
        assertFalse(ran.isDone());

        bridge.receive(BridgeCodec.encode(new BridgeMessage.PlayerCommandResult(request.requestId(), player.getUuid(),
                BridgeMessage.PlayerCommandResult.Outcome.NOT_FOUND)), player.getUsername());

        assertFalse(ran.get(1, TimeUnit.SECONDS));
        assertEquals(List.of("[Lobby] /queue bedwars is not available to you on this network."), plain(chat));
    }

    @Test
    void anExecutedPlayerCommandSaysNothing(Env env) throws Exception {
        BridgeService bridge = new BridgeService(true);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 42, 0));
        Collector<PluginMessagePacket> outgoing = connection.trackIncoming(PluginMessagePacket.class);
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        CompletableFuture<Boolean> ran = new BridgeProxyCommands(bridge, text()).run(player, "server bedwars", "test");
        BridgeMessage.PlayerCommandRequest request = (BridgeMessage.PlayerCommandRequest) outgoing.collect().stream()
                .filter(packet -> packet.channel().equals(BridgeProtocol.CHANNEL))
                .map(packet -> decode(packet.data())).findFirst().orElseThrow();
        bridge.receive(BridgeCodec.encode(new BridgeMessage.PlayerCommandResult(request.requestId(), player.getUuid(),
                BridgeMessage.PlayerCommandResult.Outcome.EXECUTED)), player.getUsername());

        assertTrue(ran.get(1, TimeUnit.SECONDS));
        assertEquals(List.of(), plain(chat));
    }

    @Test
    void anOlderBridgeIsNotAskedToRunPlayerCommands(Env env) throws Exception {
        BridgeService bridge = new BridgeService(true);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 42, 0));
        bridge.receive(BridgeCodec.encode(new BridgeMessage.ProxyInfo("0.4.0", "Velocity 3.5.1", true, true, true,
                true)), player.getUsername());
        Collector<PluginMessagePacket> outgoing = connection.trackIncoming(PluginMessagePacket.class);
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        assertFalse(new BridgeProxyCommands(bridge, text()).run(player, "server bedwars", "test").get(1, TimeUnit.SECONDS));

        assertEquals(List.of(), outgoing.collect(), "it would never answer");
        assertEquals(List.of("[Lobby] Could not run /server bedwars right now. Please try again later."), plain(chat));
    }

    @Test
    void bridgeVersionsAreCompared() {
        assertTrue(BridgeService.atLeast("0.5.0", "0.5.0"));
        assertTrue(BridgeService.atLeast("0.10.1", "0.5.0"));
        assertTrue(BridgeService.atLeast("1.0", "0.5.0"));
        assertTrue(BridgeService.atLeast("0.5.0-SNAPSHOT", "0.5.0"));
        assertTrue(BridgeService.atLeast("dev", "0.5.0"), "a version that cannot be read is trusted");
        assertFalse(BridgeService.atLeast("0.4.0", "0.5.0"));
        assertFalse(BridgeService.atLeast("0.4.9", "0.5.0"));
    }

    private LobbyText text() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        return new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
    }

    private static List<String> plain(Collector<SystemChatPacket> chat) {
        return chat.collect().stream().filter(packet -> !packet.overlay())
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message())).toList();
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
