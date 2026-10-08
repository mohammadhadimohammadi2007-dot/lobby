package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs action lists for a fake player and checks what they receive. */
@EnvTest
class ActionEnvTest {

    @TempDir
    Path dir;

    private ActionServices services() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        return new ActionServices(config, new LobbyText(config, new PlaceholderService(new PlaceholderRegistry())),
                new OperatorPermissionService(config), new BridgeService(false));
    }

    private static ActionList list(String... lines) {
        return ActionParser.parseList(List.of((Object[]) lines), 0, "test", warning -> {
            throw new AssertionError(warning);
        });
    }

    private static List<String> lines(Collector<SystemChatPacket> chat) {
        List<String> out = new ArrayList<>();
        for (SystemChatPacket packet : chat.collect()) {
            if (!packet.overlay()) {
                out.add(PlainTextComponentSerializer.plainText().serialize(packet.message()));
            }
        }
        return out;
    }

    @Test
    void missingPermissionStopsTheList(Env env) throws Exception {
        ActionServices services = services();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 41, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        list("message: one", "need_permission: lobby.vip <red>VIP only", "message: two")
                .runFrom(0, new ActionContext(player, services, "test"));

        assertEquals(List.of("one", "VIP only"), lines(chat));
    }

    @Test
    void waitContinuesLater(Env env) throws Exception {
        ActionServices services = services();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 41, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        list("message: before", "wait: 2", "message: after").runFrom(0, new ActionContext(player, services, "test"));
        assertEquals(List.of("before"), lines(chat));

        Collector<SystemChatPacket> later = connection.trackIncoming(SystemChatPacket.class);
        for (int i = 0; i < 3; i++) {
            env.tick();
        }
        long deadline = System.currentTimeMillis() + 5000;
        List<String> received = List.of();
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            received = lines(later);
        }
        assertEquals(List.of("after"), received);
    }

    @Test
    void connectWithoutBridgeSaysNotAvailable(Env env) throws Exception {
        ActionServices services = services();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(env.createFlatInstance(), new Pos(0, 41, 0));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        list("connect_group: bedwars").runFrom(0, new ActionContext(player, services, "test"));

        assertEquals(List.of("[Lobby] Switching servers is not available right now."), lines(chat));
    }
}
