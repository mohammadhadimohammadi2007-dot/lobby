package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a hologram costs with a full lobby. The question is how often its text is built: once for
 * everybody, or once per player. A player count is the same for all 200 players, so it must be built
 * once; a player's own name cannot be.
 */
@EnvTest
class HologramScaleTest {

    private static final Pos ORIGIN = new Pos(0.5, 41, 0.5);
    private static final int VIEWERS = 200;

    @TempDir
    Path dir;

    private final List<HologramService> started = new ArrayList<>();

    @AfterEach
    void stopServices() {
        started.forEach(HologramService::shutdown);
    }

    /** Players whose name contains this are treated as 1.8 clients by the test. */
    private static final String LEGACY_MARK = "L";
    /** Players whose name contains this get their text reshaped for right-to-left reading. */
    private static final String RTL_MARK = "R";

    private HologramService service(InstanceContainer map) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, info(map), LobbyInstanceInfo.SINGLE));
        LobbyText text = new LobbyText(config, placeholders);
        // Like the chat system does for players who asked for the Persian fix.
        text.viewerTransform((player, component) -> ComponentTransforms.reshapePersian(component),
                player -> player.getUsername().contains(RTL_MARK));
        HologramService service = HologramService.start(dir, renderer,
                new Hologram.Services(text, permissions,
                        player -> player.getUsername().contains(LEGACY_MARK), WorldScope.mainMap(map)),
                new ActionServices(config, text, permissions, bridge));
        started.add(service);
        return service;
    }

    private final ClientObjectRenderer renderer = new ClientObjectRenderer();

    private static List<SpawnEntityPacket> displays(Collector<SpawnEntityPacket> collector) {
        return collector.collect().stream()
                .filter(packet -> packet.type().equals(EntityType.TEXT_DISPLAY))
                .toList();
    }

    @Test
    void aGlobalPlaceholderIsBuiltOnceForTwoHundredPlayers(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        List<TestConnection> connections = join(env, map, VIEWERS);
        HologramData data = holograms.create("counter", HologramType.TEXT, ORIGIN,
                List.of("%group_online_bedwars% playing"));
        assertNotNull(data);
        assertEquals(PlaceholderScope.GLOBAL, data.textScope(),
                "a group player count is the same for every player");

        int before = renderer.renderCount();
        renderer.refreshNow();
        int renders = renderer.renderCount() - before;

        System.out.println("global hologram, " + VIEWERS + " viewers: " + renders + " render(s) per update");
        assertEquals(1, renders, "a global line must be built once and shared with every viewer");
        // Every viewer still gets it: one entity, one spawn each.
        var spawns = connections.getFirst().trackIncoming(SpawnEntityPacket.class);
        renderer.refreshNow();
        assertEquals(List.of(), displays(spawns), "nothing is respawned on the next update");

        // The value did not change, so no metadata is sent either.
        var updates = connections.get(1).trackIncoming(EntityMetaDataPacket.class);
        renderer.refreshNow();
        assertEquals(List.of(), updates.collect(), "unchanged text must not send packets");
        renderer.shutdown();
    }

    @Test
    void aStaticHologramIsNeverRebuilt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        join(env, map, 10);
        HologramData data = holograms.create("welcome", HologramType.TEXT, ORIGIN, List.of("<gold>Welcome"));
        assertNotNull(data);
        assertEquals(PlaceholderScope.STATIC, data.textScope());
        assertEquals(0, data.effectiveUpdateIntervalTicks(), "text that cannot change needs no interval");

        int before = renderer.renderCount();
        renderer.refreshNow();
        renderer.refreshNow();
        renderer.refreshNow();

        assertEquals(1, renderer.renderCount() - before, "built once, then never again");
        renderer.shutdown();
    }

    @Test
    void aPerPlayerPlaceholderIsBuiltOnlyForTheViewersInRange(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        int near = 20;
        join(env, map, near);
        // Players far away must not be rendered for at all, however many of them there are.
        joinAt(env, map, 50, ORIGIN.add(500, 0, 0));
        HologramData data = holograms.create("hello", HologramType.TEXT, ORIGIN,
                List.of("Hello %player_name%"));
        assertNotNull(data);
        assertEquals(PlaceholderScope.PER_PLAYER, data.textScope());

        int before = renderer.renderCount();
        renderer.refreshNow();
        int renders = renderer.renderCount() - before;

        System.out.println("per-player hologram, " + near + " viewers in range and 50 far away: "
                + renders + " render(s) per update");
        assertEquals(near, renders, "one render per viewer in range, and none for the others");
        renderer.shutdown();
    }

    @Test
    void persianTextIsAVariantNotOneRenderPerPlayer(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        // Four kinds of viewer: modern and 1.8, each with the Persian fix on and off.
        joinAt(env, map, VIEWERS, ORIGIN, number -> switch (number % 4) {
            case 0 -> "";
            case 1 -> RTL_MARK;
            case 2 -> LEGACY_MARK;
            default -> LEGACY_MARK + RTL_MARK;
        });
        HologramData data = holograms.create("fa", HologramType.TEXT, ORIGIN,
                List.of("%server_online% بازیکن آنلاین"));
        assertNotNull(data);
        assertEquals(PlaceholderScope.GLOBAL, data.textScope(), "a player count is the same for everyone");
        assertTrue(data.reshapeMatters(), "the line has Arabic-script letters");

        int before = renderer.renderCount();
        renderer.refreshNow();
        int renders = renderer.renderCount() - before;

        System.out.println("global Persian hologram, " + VIEWERS + " viewers in 4 groups: "
                + renders + " render(s) per update");
        assertEquals(4, renders, "one render per client generation and Persian setting, not per player");

        // A Latin line has no second version, so the Persian setting does not split its viewers.
        HologramData latin = holograms.create("en", HologramType.TEXT, ORIGIN, List.of("%server_online% online"));
        assertNotNull(latin);
        assertFalse(latin.reshapeMatters());
        before = renderer.renderCount();
        renderer.refreshNow();
        System.out.println("global Latin hologram, " + VIEWERS + " viewers in 4 groups: "
                + (renderer.renderCount() - before) + " render(s) per update");
        assertEquals(2, renderer.renderCount() - before, "only the two client generations");
        renderer.shutdown();
    }

    @Test
    void manyHologramsWithOneGlobalLineStayCheap(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        join(env, map, VIEWERS);
        int hologramCount = 50;
        for (int number = 1; number <= hologramCount; number++) {
            holograms.create("sign-" + number, HologramType.TEXT, ORIGIN.add(number % 10, 0, number / 10),
                    List.of("<gold>Shop " + number, "<gray>%server_online% online"));
        }

        int before = renderer.renderCount();
        long startNanos = System.nanoTime();
        renderer.refreshNow();
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        int renders = renderer.renderCount() - before;

        // The second refresh is the steady state: everything is spawned and nothing changed.
        long steadyStart = System.nanoTime();
        renderer.refreshNow();
        long steadyMillis = (System.nanoTime() - steadyStart) / 1_000_000;

        System.out.println(hologramCount + " holograms, " + VIEWERS + " viewers: " + renders
                + " renders in " + millis + " ms (first refresh, including every spawn packet), "
                + steadyMillis + " ms for the next refresh with nothing changed");
        assertEquals(hologramCount, renders, "one render per hologram, not per hologram per viewer");
        assertEquals(renders, renderer.renderCount() - before, "nothing is rebuilt while nothing changes");
        assertTrue(millis < 2000, "the first refresh of 50 holograms took " + millis + " ms");
        renderer.shutdown();
    }

    @Test
    void oneJoiningPlayerAndAReloadStayCheap(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        HologramService holograms = service(map);
        join(env, map, VIEWERS);
        int hologramCount = 50;
        for (int number = 1; number <= hologramCount; number++) {
            holograms.create("sign-" + number, HologramType.TEXT, ORIGIN.add(number % 10, 0, number / 10),
                    List.of("<gold>Shop " + number));
        }
        renderer.refreshNow();
        // A refresh with nothing new, to compare against.
        long idleStart = System.nanoTime();
        renderer.refreshNow();
        long idleMillis = (System.nanoTime() - idleStart) / 1_000_000;

        // One more player joins: every hologram has to be spawned for them, and for nobody else.
        join(env, map, 1);
        long joinStart = System.nanoTime();
        renderer.refreshNow();
        long joinMillis = (System.nanoTime() - joinStart) / 1_000_000;

        // A reload: read the file again, despawn everything for 200 players, show it again.
        long reloadStart = System.nanoTime();
        holograms.reload();
        renderer.refreshNow();
        long reloadMillis = (System.nanoTime() - reloadStart) / 1_000_000;

        System.out.println(hologramCount + " holograms, " + VIEWERS + " viewers: idle refresh "
                + idleMillis + " ms, one player joining " + joinMillis + " ms (" + (joinMillis - idleMillis)
                + " ms more than idle), /hologram reload " + reloadMillis + " ms");
        assertEquals(hologramCount, holograms.count(), "the reload found every hologram again");
        assertTrue(joinMillis - idleMillis < 50,
                "spawning " + hologramCount + " holograms for one joining player took "
                        + (joinMillis - idleMillis) + " ms more than an idle refresh");
        assertTrue(reloadMillis < 2000, "a reload took " + reloadMillis + " ms");
        renderer.shutdown();
    }

    private List<TestConnection> join(Env env, Instance instance, int count) {
        return joinAt(env, instance, count, ORIGIN);
    }

    private List<TestConnection> joinAt(Env env, Instance instance, int count, Pos where) {
        return joinAt(env, instance, count, where, number -> "");
    }

    /**
     * Joins {@code count} players, with {@code suffix} deciding each one's client generation and
     * Persian setting from its number.
     */
    private List<TestConnection> joinAt(Env env, Instance instance, int count, Pos where,
                                        java.util.function.IntFunction<String> suffix) {
        List<TestConnection> connections = new ArrayList<>(count);
        for (int number = 0; number < count; number++) {
            TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(),
                    "p" + where.blockX() + "x" + number + suffix.apply(number)));
            connection.connect(instance, where);
            connections.add(connection);
        }
        return connections;
    }

    private static ServerInfo info(InstanceContainer map) {
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
}
