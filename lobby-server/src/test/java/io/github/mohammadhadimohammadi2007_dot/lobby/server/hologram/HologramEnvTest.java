package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holograms on a running server: who sees them, what a click does, and what is written to disk. */
@EnvTest
class HologramEnvTest {

    private static final Pos ORIGIN = new Pos(0.5, 41, 0.5);
    private static final Pos TELEPORT_TARGET = new Pos(100, 41, 100);

    @TempDir
    Path dir;

    private final List<HologramService> started = new java.util.ArrayList<>();

    /** Saving runs in the background, so every service is closed before the temp folder is deleted. */
    @AfterEach
    void stopServices() {
        started.forEach(HologramService::shutdown);
    }

    /** A running hologram service with its renderer and click handling, on the given map. */
    private record Setup(HologramService holograms, ClientObjectRenderer renderer, ConfigManager config) {
    }

    private Setup start(Env env, InstanceContainer map) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Path main = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(main, Files.readString(main).replace("operators: []", "operators: [\"Admin\"]"));
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
        // The real placeholders, so %server_online% is known and counts as a global one.
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, serverInfo(map), LobbyInstanceInfo.SINGLE));
        LobbyText text = new LobbyText(config, placeholders);
        ActionServices actions = new ActionServices(config, text, permissions, bridge);
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        ClientObjectClicks clicks = new ClientObjectClicks(renderer);
        HologramService holograms = HologramService.start(dir, renderer,
                new Hologram.Services(text, permissions, bridge, WorldScope.mainMap(map)), actions);
        clicks.addHandler(holograms);
        started.add(holograms);
        EventNode<PlayerEvent> node = EventNode.type("hologram-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        clicks.register(node);
        return new Setup(holograms, renderer, config);
    }

    private Player join(Env env, Instance instance, String name, Pos at) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        return connection.connect(instance, at);
    }

    private static List<SpawnEntityPacket> displays(Collector<SpawnEntityPacket> collector) {
        return collector.collect().stream()
                .filter(packet -> packet.type().equals(EntityType.TEXT_DISPLAY))
                .toList();
    }

    /** Enough of a ServerInfo for the built-in placeholders. */
    private static ServerInfo serverInfo(InstanceContainer map) {
        LobbyWorld world = new LobbyWorld(map, "test-map", WorldFormat.POLAR, 1, 0, 0, false);
        return new ServerInfo() {
            @Override
            public String modeDescription() {
                return "standalone";
            }

            @Override
            public double tps() {
                return 20;
            }

            @Override
            public double mspt() {
                return 1;
            }

            @Override
            public LobbyWorld world() {
                return world;
            }

            @Override
            public List<IntegrationStatus> integrations() {
                return List.of();
            }

            @Override
            public String bridgeStatus() {
                return "off";
            }
        };
    }

    private static void sendPacket(Env env, Player player, ClientPacket packet) {
        env.process().eventHandler().call(new PlayerPacketEvent(player, packet));
    }

    @Test
    void aHologramIsShownToPlayersNearItOnly(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection nearConnection = env.createConnection(new GameProfile(UUID.randomUUID(), "Near"));
        TestConnection farConnection = env.createConnection(new GameProfile(UUID.randomUUID(), "Far"));
        nearConnection.connect(map, ORIGIN);
        farConnection.connect(map, ORIGIN.add(200, 0, 0));
        setup.holograms().create("welcome", HologramType.TEXT, ORIGIN.add(0, 2, 0), List.of("<gold>Welcome"));
        var nearSpawns = nearConnection.trackIncoming(SpawnEntityPacket.class);
        var farSpawns = farConnection.trackIncoming(SpawnEntityPacket.class);

        setup.renderer().refreshNow();

        assertEquals(1, displays(nearSpawns).size());
        assertEquals(List.of(), displays(farSpawns), "200 blocks away is outside the view distance");
        setup.renderer().shutdown();
    }

    @Test
    void aPermissionHidesItFromPlayersWithoutIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection staffConnection = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin"));
        TestConnection playerConnection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        staffConnection.connect(map, ORIGIN);
        playerConnection.connect(map, ORIGIN);
        HologramData data = setup.holograms().create("staff-only", HologramType.TEXT, ORIGIN, List.of("secret"));
        assertNotNull(data);
        data.permission("lobby.command.reload");
        setup.holograms().changed(data);
        var staffSpawns = staffConnection.trackIncoming(SpawnEntityPacket.class);
        var playerSpawns = playerConnection.trackIncoming(SpawnEntityPacket.class);

        setup.renderer().refreshNow();

        // Operators have every permission; Steve is not one.
        assertEquals(1, displays(staffSpawns).size());
        assertEquals(List.of(), displays(playerSpawns));
        setup.renderer().shutdown();
    }

    @Test
    void clickingRunsTheActionsAndOnlyTheMainHandCounts(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(map, ORIGIN);
        HologramData data = setup.holograms().create("shop", HologramType.TEXT, ORIGIN, List.of("Click me"));
        assertNotNull(data);
        setup.holograms().setActions(data,
                List.of("teleport: " + TELEPORT_TARGET.x() + " " + TELEPORT_TARGET.y() + " " + TELEPORT_TARGET.z()),
                0);
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        setup.renderer().refreshNow();
        int entityId = displays(spawns).getFirst().entityId();

        // The off hand must be ignored: a client sends the interact packet for both hands.
        sendPacket(env, steve, new ClientInteractEntityPacket(entityId, PlayerHand.OFF, Vec.ZERO, false));
        env.tickWhile(() -> steve.getPosition().x() < TELEPORT_TARGET.x(), Duration.ofMillis(300));
        assertEquals(ORIGIN.x(), steve.getPosition().x(), "the off hand did nothing");

        // A left click (attack) runs the same actions.
        sendPacket(env, steve, new ClientAttackPacket(entityId));
        assertTrue(env.tickWhile(() -> steve.getPosition().x() < TELEPORT_TARGET.x(), Duration.ofSeconds(5)),
                "the click should have run the teleport action");
        assertEquals(TELEPORT_TARGET.x(), steve.getPosition().x());
        setup.renderer().shutdown();
    }

    @Test
    void aClickOnSomethingElseIsIgnored(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(map, ORIGIN);
        HologramData data = setup.holograms().create("shop", HologramType.TEXT, ORIGIN, List.of("Click me"));
        assertNotNull(data);
        setup.holograms().setActions(data, List.of("teleport: 100 41 100"), 0);
        setup.renderer().refreshNow();

        // An entity id that belongs to no hologram (another player, a mob) must do nothing at all.
        sendPacket(env, steve, new ClientAttackPacket(123_456));
        env.tickWhile(() -> steve.getPosition().x() < TELEPORT_TARGET.x(), Duration.ofMillis(300));

        assertEquals(ORIGIN.x(), steve.getPosition().x());
        setup.renderer().shutdown();
    }

    @Test
    void deletingDespawnsItAndSavesTheFile(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        connection.connect(map, ORIGIN);
        setup.holograms().create("welcome", HologramType.TEXT, ORIGIN, List.of("Hello"));
        setup.renderer().refreshNow();
        var destroys = connection.trackIncoming(DestroyEntitiesPacket.class);

        assertTrue(setup.holograms().delete("welcome"));
        setup.renderer().refreshNow();

        assertEquals(1, destroys.collect().size());
        assertEquals(0, setup.holograms().count());
        setup.holograms().shutdown();
        String written = Files.readString(setup.holograms().file());
        assertFalse(written.contains("welcome"), "the deleted hologram is gone from the file: " + written);
        setup.renderer().shutdown();
    }

    @Test
    void thePlayerCountInAHologramIsRebuiltWithoutRespawningIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        connection.connect(map, ORIGIN);
        HologramData data = setup.holograms().create("counter", HologramType.TEXT, ORIGIN,
                List.of("Players: %server_online%"));
        assertNotNull(data);
        assertEquals(HologramData.PLACEHOLDER_UPDATE_TICKS, data.effectiveUpdateIntervalTicks(),
                "text with a placeholder is rebuilt by itself");
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        setup.renderer().refreshNow();
        assertEquals(1, displays(spawns).size());

        var moreSpawns = connection.trackIncoming(SpawnEntityPacket.class);
        setup.renderer().refreshNow();

        assertEquals(List.of(), displays(moreSpawns), "a rebuild must not respawn the entity");
        setup.renderer().shutdown();
    }
}
