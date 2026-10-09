package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.DisplayLoad;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcSkins;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display thread with everything on it at once and 200 players: 50 holograms, 30 NPCs, a scoreboard,
 * the tab list, nametags and the boss bar, with values that change every second (one for everybody, one
 * per player), so lines really are sent again every second.
 *
 * <p>One simulated second is what the server does in one real second: 4 hologram/NPC refreshes (5 ticks
 * apart, so update intervals come round as they really do) and 10 display refreshes (every 2 ticks,
 * the one on the second boundary doing the scoreboard lines, tab list and boss bar). The time those take
 * on the display thread, divided by a second, is its utilization.
 */
@EnvTest
class DisplayScaleTest {

    private static final int PLAYERS = 200;
    private static final int GLOBAL_HOLOGRAMS = 40;
    private static final int PLAYER_HOLOGRAMS = 10;
    private static final int GLOBAL_NPCS = 20;
    private static final int PLAYER_NPCS = 10;
    private static final int WARMUP_SECONDS = 3;
    private static final int MEASURED_SECONDS = 20;
    private static final int RENDERER_REFRESHES_PER_SECOND = 4;
    private static final int DISPLAY_REFRESHES_PER_SECOND = 10;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int TICKS_PER_RENDERER_REFRESH = 5;

    private static final String DISPLAY = """
            scoreboard:
              enabled: true
              boards:
                default:
                  title: ["<gold><bold>LOBBY", "<yellow><bold>LOBBY"]
                  title-interval: 10
                  lines:
                    - ""
                    - "<white>%player_name%"
                    - "<gray>Rank: %luckperms_prefix%"
                    - "<gray>Coins: <gold>%test_coins%"
                    - ""
                    - "<gray>Clock: <white>%test_clock%"
                    - "<gray>Online: <green>%server_online%"
                    - "<gray>Lobby: <yellow>#%lobby_id%"
                    - ""
                    - "<yellow>play.example.net"
            tab:
              enabled: true
              header: ["<gold><bold>EXAMPLE NETWORK", "<gray>Clock %test_clock%"]
              footer: ["<gray>Coins %test_coins%"]
              name-format: "%luckperms_prefix%%player_name%"
              show: server
              group-order: [admin, vip, default]
            nametags:
              enabled: true
              prefix: "%luckperms_prefix%"
              suffix: ""
            bossbar:
              enabled: true
              interval: 100
              messages:
                - text: "<yellow>Welcome %player_name%, it is %test_clock%"
                - text: "<green>%server_online% online"
            join-title: {enabled: false}
            """;

    @TempDir
    Path dir;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void stop() {
        cleanup.forEach(Runnable::run);
    }

    @Test
    void everythingTogetherWithTwoHundredPlayers(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = new DisplayTestServer(env, dir, map, DISPLAY);
        cleanup.add(server::shutdown);
        AtomicInteger second = new AtomicInteger();
        server.placeholders.registry().register("test", params -> switch (params) {
            case "clock" -> Placeholder.global(Duration.ofMillis(1), () -> "12:" + second.get());
            case "coins" -> Placeholder.player(Duration.ofMillis(1),
                    player -> Integer.toString(second.get() * 7 + player.getUsername().length()));
            default -> null;
        });

        BridgeService bridge = new BridgeService(false);
        Hologram.Services services = new Hologram.Services(server.text, server.permissions,
                player -> DisplayTestServer.protocolOf(player) < 762, WorldScope.mainMap(map));
        ActionServices actions = new ActionServices(server.config, server.text, server.permissions, bridge);
        HologramService holograms = HologramService.start(dir, server.renderer, services, actions);
        cleanup.add(holograms::shutdown);
        NpcService npcs = NpcService.start(dir, server.renderer, services, actions, new NpcSkins(null, null),
                server.teams);
        cleanup.add(npcs::shutdown);
        for (int i = 0; i < GLOBAL_HOLOGRAMS + PLAYER_HOLOGRAMS; i++) {
            String line = i < GLOBAL_HOLOGRAMS ? "<gold>Clock %test_clock%" : "<gray>Hi %player_name%, %test_coins% coins";
            HologramData data = holograms.create("h" + i, HologramType.TEXT, spot(i), List.of(line, "<gray>line two"));
            assertNotNull(data);
        }
        for (int i = 0; i < GLOBAL_NPCS + PLAYER_NPCS; i++) {
            NpcData npc = npcs.create("n" + i, spot(i).add(0, 0, 3));
            assertNotNull(npc);
            npc.nameTag().frames(List.of(List.of(i < GLOBAL_NPCS ? "<yellow>Game " + i + " - %test_clock%"
                    : "<aqua>Hello %player_name%")));
            npc.turnToPlayer(i % 3 == 0);
            npcs.changed(npc);
        }
        for (int i = 0; i < PLAYERS; i++) {
            String tier = switch (i % 4) {
                case 0 -> "Old";
                case 1 -> "Mid";
                default -> "";
            };
            String rank = i % 10 == 0 ? "Admin" : i % 5 == 0 ? "Vip" : "P";
            TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), rank + tier + i));
            connection.connect(map, spot(i));
        }

        // Everything appearing at once for 200 players who are already there (as after /lobby reload): the
        // renderer spreads it over refreshes of about 50 ms each instead of one long one.
        long appearLongest = 0;
        long appearTotal = 0;
        int appearRefreshes = 0;
        while (server.renderer.objectCount() > shownObjects(server)) {
            long start = System.nanoTime();
            server.renderer.refreshNow(TICKS_PER_RENDERER_REFRESH);
            long took = System.nanoTime() - start;
            appearLongest = Math.max(appearLongest, took);
            appearTotal += took;
            appearRefreshes++;
        }
        long firstDisplayStart = System.nanoTime();
        server.displays.refreshNow(DisplayService.STEP_TICKS);
        long firstDisplayMillis = (System.nanoTime() - firstDisplayStart) / 1_000_000;
        System.out.println(String.format(Locale.ROOT, "everything appearing for %d players at once: holograms/NPCs in %d"
                        + " refreshes, longest %.1f ms, %.1f ms in all; first scoreboard/tab/bars refresh %d ms",
                PLAYERS, appearRefreshes, appearLongest / NANOS_PER_MILLI, appearTotal / NANOS_PER_MILLI,
                firstDisplayMillis));
        for (int i = 0; i < WARMUP_SECONDS; i++) {
            simulateSecond(server, second);
        }
        // One more player joining a full lobby: the real-life case of the burst above.
        env.createConnection(new GameProfile(UUID.randomUUID(), "Joiner")).connect(map, spot(7));
        long joinStart = System.nanoTime();
        server.renderer.refreshNow(TICKS_PER_RENDERER_REFRESH);
        long joinRenderer = System.nanoTime() - joinStart;
        joinStart = System.nanoTime();
        server.displays.refreshNow(20);
        long joinDisplay = System.nanoTime() - joinStart;
        System.out.println(String.format(Locale.ROOT, "one player joining %d: holograms/NPCs refresh %.1f ms,"
                        + " scoreboard/tab/bars refresh %.1f ms (including the normal work of that refresh)",
                PLAYERS, joinRenderer / NANOS_PER_MILLI, joinDisplay / NANOS_PER_MILLI));
        java.util.Arrays.fill(longestByKind, 0);
        callsByKind.forEach(List::clear);
        long gcBefore = gcMillis();
        long[] busy = new long[MEASURED_SECONDS];
        long longest = 0;
        for (int i = 0; i < MEASURED_SECONDS; i++) {
            long[] result = simulateSecond(server, second);
            busy[i] = result[0];
            longest = Math.max(longest, result[1]);
        }

        long gcDuring = gcMillis() - gcBefore;
        double averageMillis = java.util.Arrays.stream(busy).average().orElse(0) / NANOS_PER_MILLI;
        double worstMillis = java.util.Arrays.stream(busy).max().orElse(0) / NANOS_PER_MILLI;
        DisplayLoad.Snapshot load = server.renderer.load();
        List<Integer> counters = server.displays.counters();
        System.out.println(String.format(Locale.ROOT,
                "display thread, %d players, %d holograms, %d NPCs, scoreboard + tab + nametags + boss bar:%n"
                        + "  busy %.1f%% on average (%.1f ms per second), %.1f%% in the worst second%n"
                        + "  longest single cycle %.1f ms (measured), %.1f ms (DisplayLoad)%n"
                        + "  texts rendered %d, sidebar lines sent %d, tab name packets %d",
                PLAYERS, GLOBAL_HOLOGRAMS + PLAYER_HOLOGRAMS, GLOBAL_NPCS + PLAYER_NPCS,
                averageMillis / 10, averageMillis, worstMillis / 10, longest / NANOS_PER_MILLI,
                load.longestCycleMillis(), counters.get(0), counters.get(1), counters.get(2)));
        System.out.println(String.format(Locale.ROOT,
                "  longest by kind: first hologram/NPC refresh of a second %.1f ms, the others %.1f ms,"
                        + " display on the second %.1f ms, display in between %.1f ms",
                longestByKind[0] / NANOS_PER_MILLI, longestByKind[1] / NANOS_PER_MILLI,
                longestByKind[2] / NANOS_PER_MILLI, longestByKind[3] / NANOS_PER_MILLI));
        String[] kinds = {"first hologram/NPC refresh", "other hologram/NPC refresh", "display on the second",
                "display in between"};
        for (int kind = 0; kind < kinds.length; kind++) {
            List<Long> sorted = callsByKind.get(kind).stream().sorted().toList();
            System.out.println(String.format(Locale.ROOT, "  %s: median %.1f ms, p99 %.1f ms (%d calls)", kinds[kind],
                    sorted.get(sorted.size() / 2) / NANOS_PER_MILLI,
                    sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99))) / NANOS_PER_MILLI,
                    sorted.size()));
        }
        System.out.println("  garbage collection during the " + MEASURED_SECONDS + " measured seconds: " + gcDuring
                + " ms, heap max " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        // The target is well under half; this only catches a big step backwards on a slow machine.
        assertTrue(averageMillis < 800, "the display thread was busy " + averageMillis + " ms per second");
    }

    /** Longest call of each kind: rebuilding renderer refresh, other renderer refresh, display on the second, other display. */
    private final long[] longestByKind = new long[4];
    /** Every call of each kind, for the median and the 99th percentile. */
    private final List<List<Long>> callsByKind = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());

    /** One second of work; returns the busy nanoseconds and the longest single call. */
    private long[] simulateSecond(DisplayTestServer server, AtomicInteger second) {
        second.incrementAndGet();
        long busy = 0;
        long longest = 0;
        int displayRefreshes = 0;
        for (int step = 0; step < RENDERER_REFRESHES_PER_SECOND; step++) {
            long start = System.nanoTime();
            // Five ticks pass between two refreshes, so update intervals come round as on a real server.
            server.renderer.refreshNow(TICKS_PER_RENDERER_REFRESH);
            long took = System.nanoTime() - start;
            busy += took;
            longest = Math.max(longest, took);
            longestByKind[step == 0 ? 0 : 1] = Math.max(longestByKind[step == 0 ? 0 : 1], took);
            callsByKind.get(step == 0 ? 0 : 1).add(took);
            // The display refreshes in between, 10 a second in all.
            int until = (step + 1) * DISPLAY_REFRESHES_PER_SECOND / RENDERER_REFRESHES_PER_SECOND;
            for (; displayRefreshes < until; displayRefreshes++) {
                start = System.nanoTime();
                server.displays.refreshNow(DisplayService.STEP_TICKS);
                took = System.nanoTime() - start;
                busy += took;
                longest = Math.max(longest, took);
                int kind = displayRefreshes == DISPLAY_REFRESHES_PER_SECOND - 1 ? 2 : 3;
                longestByKind[kind] = Math.max(longestByKind[kind], took);
                callsByKind.get(kind).add(took);
            }
        }
        return new long[]{busy, longest};
    }

    private static int shownObjects(DisplayTestServer server) {
        return server.renderer.shownObjectCount();
    }

    private static long gcMillis() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionTime).sum();
    }

    /** Spread out within a few blocks, so every player sees every hologram and NPC. */
    private static Pos spot(int index) {
        return DisplayTestServer.ORIGIN.add(index % 20 - 10, 0, (index / 20) % 10 - 5);
    }
}
