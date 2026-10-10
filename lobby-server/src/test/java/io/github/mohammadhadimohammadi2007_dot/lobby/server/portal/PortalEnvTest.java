package io.github.mohammadhadimohammadi2007_dot.lobby.server.portal;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Portals on a running server: making one, saving it, walking in, and being pushed back. */
@EnvTest
class PortalEnvTest {

    private static final Pos OUTSIDE = new Pos(0.5, 41, 0.5, 90, 0);

    @TempDir
    Path dir;

    private final List<PortalService> started = new ArrayList<>();

    @AfterEach
    void stop() {
        started.forEach(PortalService::shutdown);
    }

    private PortalService start(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        // No proxy here, so every connect fails: just what the push-back needs.
        ActionServices actions = new ActionServices(config, text, permissions, new BridgeService(false));
        RegionTracker regions = new RegionTracker();
        PortalService portals = PortalService.start(dir, regions, actions, text);
        started.add(portals);
        EventNode<PlayerEvent> node = EventNode.type("portal-test-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        regions.register(node);
        portals.register(node);
        return portals;
    }

    private static Portal make(PortalService portals, Player admin, String name, List<Object> actions) {
        portals.select(admin, 0, new Vec(3, 41, -1));
        portals.select(admin, 1, new Vec(4, 43, 1));
        assertEquals(PortalService.CreateResult.CREATED, portals.create(admin, name));
        Portal portal = portals.get(name);
        assertNotNull(portal);
        portals.update(portal.withActions(actions, warning -> { }));
        return portals.get(name);
    }

    @Test
    void aPortalIsMadeFromTheSelectionAndSaved(Env env) throws Exception {
        PortalService portals = start(env);
        Instance instance = env.createFlatInstance();
        Player admin = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin")).connect(instance, OUTSIDE);

        assertEquals(PortalService.CreateResult.NO_SELECTION, portals.create(admin, "bedwars"));
        Portal portal = make(portals, admin, "bedwars", List.of("connect_group: bedwars"));
        assertEquals(PortalService.CreateResult.EXISTS, portals.create(admin, "bedwars"));
        assertEquals(PortalService.CreateResult.BAD_NAME, portals.create(admin, "no spaces"));
        assertEquals(2 * 3 * 3, portal.region().volume());

        portals.shutdown();
        String file = Files.readString(portals.file());
        assertTrue(file.contains("connect_group: bedwars"), file);
        PortalService again = start(env);
        Portal loaded = again.get("bedwars");
        assertNotNull(loaded);
        assertEquals(portal.region(), loaded.region());
        assertEquals(List.of("connect_group: bedwars"), loaded.entries());
    }

    @Test
    void walkingInRunsTheActions(Env env) throws Exception {
        PortalService portals = start(env);
        Instance instance = env.createFlatInstance();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(instance, OUTSIDE);
        make(portals, player, "hello", List.of("message: <green>Hello from the portal"));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().eventHandler().call(new PlayerMoveEvent(player, new Pos(3.5, 41, 0.5), true));
        List<String> received = new ArrayList<>();
        // Actions run in the background, so wait for the message to arrive.
        env.tickWhile(() -> {
            chat.collect().forEach(packet -> received.add(PlainTextComponentSerializer.plainText().serialize(packet.message())));
            return received.isEmpty();
        }, Duration.ofSeconds(5));

        assertEquals(List.of("Hello from the portal"), received);
    }

    @Test
    void aFailedConnectPushesThePlayerBackOut(Env env) throws Exception {
        PortalService portals = start(env);
        Instance instance = env.createFlatInstance();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(instance, OUTSIDE);
        make(portals, player, "bedwars", List.of("connect_group: bedwars"));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().eventHandler().call(new PlayerMoveEvent(player, new Pos(3.5, 41, 0.5), true));
        // The move itself is applied by the client; here it is as if it had happened.
        player.teleport(new Pos(3.5, 41, 0.5)).join();
        env.tickWhile(() -> player.getPosition().x() > 1, Duration.ofSeconds(5));

        assertEquals(OUTSIDE.x(), player.getPosition().x(), 0.01, "back where they stepped in");
        assertTrue(player.getVelocity().x() < 0, "pushed away from the portal: " + player.getVelocity());
        List<String> lines = chat.collect().stream()
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message())).toList();
        assertTrue(lines.stream().anyMatch(line -> line.contains("not available")), "told why: " + lines);
    }
}
