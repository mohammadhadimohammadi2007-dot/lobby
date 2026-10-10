package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.DisplayLoad;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.VisibilityService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.movement.MovementService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcSkins;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.PortalService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole lobby under load: 200 players walking in circles for five minutes (real time, 20 ticks a
 * second), with 50 holograms and 30 NPCs (some with per-player text), the bundled scoreboard, tab list,
 * nametags and boss bar, hotbar items, player visibility, regions, jump pads and portals all running.
 * Reports the tick time per minute (to see it does not grow), the display thread, and allocation and GC.
 *
 * <p>Off by default, like the chat load test: {@code LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test
 * --tests '*LobbyLoadTest'}. {@code LOBBY_LOAD_TEST_SECONDS} changes the duration (300 by default).
 */
@EnvTest
@EnabledIfEnvironmentVariable(named = "LOBBY_LOAD_TEST", matches = "1")
class LobbyLoadTest {

    private static final int PLAYERS = 200;
    private static final int HOLOGRAMS = 50;
    private static final int NPCS = 30;
    private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final double CIRCLE_RADIUS = 6;

    @TempDir
    Path dir;

    private final List<Runnable> cleanup = new ArrayList<>();

    @AfterEach
    void stop() {
        cleanup.forEach(Runnable::run);
    }

    @Test
    void twoHundredPlayersWalkingWithEverythingOn(Env env) throws Exception {
        int seconds = Integer.parseInt(System.getenv().getOrDefault("LOBBY_LOAD_TEST_SECONDS", "300"));
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        // The bundled display.yml: what a server gets out of the box.
        DisplayTestServer server = new DisplayTestServer(env, dir, map, null);
        cleanup.add(server::shutdown);
        BridgeService bridge = new BridgeService(false);
        ActionServices actions = new ActionServices(server.config, server.text, server.permissions, bridge);
        MenuService menus = new MenuService(() -> server.config.current().menus(), server.text, actions, bridge);
        actions.menus(menus);
        Hologram.Services services = new Hologram.Services(server.text, server.permissions,
                player -> DisplayTestServer.protocolOf(player) < 762, WorldScope.mainMap(map));
        HologramService holograms = HologramService.start(dir, server.renderer, services, actions);
        cleanup.add(holograms::shutdown);
        NpcService npcs = NpcService.start(dir, server.renderer, services, actions, new NpcSkins(null, null), server.teams);
        cleanup.add(npcs::shutdown);
        for (int i = 0; i < HOLOGRAMS; i++) {
            String line = i % 5 == 0 ? "<gray>Hi %player_name%, ping %player_ping%" : "<gold>%server_online% online";
            holograms.create("h" + i, HologramType.TEXT, spot(i, 12), List.of(line, "<gray>Lobby #%lobby_id%"));
        }
        for (int i = 0; i < NPCS; i++) {
            NpcData npc = npcs.create("n" + i, spot(i, 16));
            npc.nameTag().frames(List.of(List.of(i % 3 == 0 ? "<aqua>Hello %player_name%" : "<yellow>Game " + i)));
            npc.turnToPlayer(i % 2 == 0);
            npcs.changed(npc);
        }
        ChatSettingsService settings = new ChatSettingsService(new FileSettingsStore(dir));
        cleanup.add(settings::flush);
        VisibilityService visibility = new VisibilityService(server.config, server.permissions, settings, server.text);
        HotbarService hotbar = new HotbarService(server.config, server.text, actions, bridge, visibility);
        RegionTracker regions = new RegionTracker();
        MovementService movement = new MovementService(server.config, server.permissions, regions);
        PortalService portals = PortalService.start(dir, regions, actions, server.text);
        cleanup.add(portals::shutdown);
        EventNode<PlayerEvent> node = EventNode.type("load-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        server.renderer.register(node);
        server.displays.register(node);
        hotbar.register(node);
        regions.register(node);
        movement.register(node);
        portals.register(node);

        List<Player> players = new ArrayList<>();
        long joinLongest = 0;
        for (int i = 0; i < PLAYERS; i++) {
            String tier = switch (i % 4) {
                case 0 -> "Old";
                case 1 -> "Mid";
                default -> "";
            };
            UUID id = UUID.randomUUID();
            settings.load(id);
            players.add(env.createConnection(new GameProfile(id, "P" + tier + i)).connect(map, spot(i, 4)));
            // One join per tick, as when a full lobby fills up, with everyone already there walking.
            walk(players, i);
            long joinStart = System.nanoTime();
            env.tick();
            joinLongest = Math.max(joinLongest, System.nanoTime() - joinStart);
        }
        System.out.println(String.format(Locale.ROOT, "joining one per tick up to %d players: longest tick %.1f ms",
                PLAYERS, joinLongest / 1e6));

        // Settling down after the last join before measuring.
        int warmupTicks = 20 * Integer.parseInt(System.getenv().getOrDefault("LOBBY_LOAD_TEST_WARMUP", "15"));
        long warmupNext = System.nanoTime();
        long warmupMax = 0;
        int warmupMaxAt = 0;
        List<String> firstTicks = new ArrayList<>();
        for (int i = 0; i < warmupTicks; i++) {
            walk(players, -warmupTicks + i);
            long tickStart = System.nanoTime();
            env.tick();
            long took = System.nanoTime() - tickStart;
            if (took > warmupMax) {
                warmupMax = took;
                warmupMaxAt = i;
            }
            if (i < 5) {
                firstTicks.add(String.format(Locale.ROOT, "%.0f", took / 1e6));
            }
            warmupNext += TICK_NANOS;
            long sleep = warmupNext - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
        }
        System.out.println(String.format(Locale.ROOT, "warm-up (%d s after the joins): longest tick %.1f ms (tick %d);"
                + " first ticks %s ms", warmupTicks / 20, warmupMax / 1e6, warmupMaxAt, String.join(", ", firstTicks)));
        List<String> spikes = new ArrayList<>();
        long gcBefore = gcMillis();
        long allocatedBefore = allocated();
        long start = System.nanoTime();
        List<Long> ticks = new ArrayList<>();
        List<DisplayLoad.Snapshot> displayPerMinute = new ArrayList<>();
        long nextTick = System.nanoTime();
        long end = start + TimeUnit.SECONDS.toNanos(seconds);
        int tick = 0;
        while (System.nanoTime() < end) {
            walk(players, tick);
            long tickStart = System.nanoTime();
            long gcAtStart = gcMillis();
            env.tick();
            long took = System.nanoTime() - tickStart;
            ticks.add(took);
            if (took > TICK_NANOS) {
                spikes.add(String.format(Locale.ROOT, "tick %d: %.1f ms (GC during it: %d ms)", tick, took / 1e6,
                        gcMillis() - gcAtStart));
            }
            tick++;
            if (tick % TICKS_PER_MINUTE == 0) {
                displayPerMinute.add(server.renderer.load());
            }
            nextTick += TICK_NANOS;
            long sleep = nextTick - System.nanoTime();
            if (sleep > 0) {
                TimeUnit.NANOSECONDS.sleep(sleep);
            }
        }
        double wallSeconds = (System.nanoTime() - start) / 1e9;
        long gc = gcMillis() - gcBefore;
        double allocatedMb = (allocated() - allocatedBefore) / (1024.0 * 1024.0);

        String report = report(ticks, displayPerMinute, server.renderer.load(), wallSeconds, gc, allocatedMb,
                server.displays.skippedRefreshes());
        System.out.println(report);
        System.out.println("  ticks over 50 ms: " + spikes.size() + (spikes.isEmpty() ? "" : " - " + String.join("; ",
                spikes.subList(0, Math.min(10, spikes.size())))));
        Path out = Path.of("build", "reports", "lobby-load-test.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        double average = ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        assertTrue(average < 25, "average tick " + average + " ms; the target is well under 50");
    }

    /** Every player walks a small circle around their own spot, one step per tick, looking ahead. */
    private static void walk(List<Player> players, int tick) {
        for (int i = 0; i < players.size(); i++) {
            Pos centre = spot(i, 4);
            double angle = (tick + i * 7) * 0.05;
            Pos to = centre.add(Math.cos(angle) * CIRCLE_RADIUS, 0, Math.sin(angle) * CIRCLE_RADIUS)
                    .withView((float) Math.toDegrees(angle), 0);
            players.get(i).addPacketToQueue(new ClientPlayerPositionAndRotationPacket(to, true, false));
        }
    }

    private static String report(List<Long> ticks, List<DisplayLoad.Snapshot> perMinute, DisplayLoad.Snapshot last,
                                 double wallSeconds, long gcMillis, double allocatedMb, int skipped) {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "lobby load test: %d players walking, %d holograms, %d NPCs, bundled scoreboard/tab/nametags/boss bar,"
                        + " hotbar, visibility, regions, pads, portals; %.0f s%n", PLAYERS, HOLOGRAMS, NPCS, wallSeconds));
        long[] all = ticks.stream().mapToLong(Long::longValue).sorted().toArray();
        text.append(String.format(Locale.ROOT, "  tick (MSPT): average %.2f ms, median %.2f, p99 %.2f, max %.2f%n",
                Arrays.stream(all).average().orElse(0) / 1e6, all[all.length / 2] / 1e6,
                all[Math.min(all.length - 1, (int) (all.length * 0.99))] / 1e6, all[all.length - 1] / 1e6));
        for (int minute = 0; minute * TICKS_PER_MINUTE < ticks.size(); minute++) {
            List<Long> slice = ticks.subList(minute * TICKS_PER_MINUTE, Math.min(ticks.size(), (minute + 1) * TICKS_PER_MINUTE));
            double average = slice.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
            long max = slice.stream().mapToLong(Long::longValue).max().orElse(0);
            String display = minute < perMinute.size() ? String.format(Locale.ROOT, "display thread %.1f%% busy, longest %.1f ms",
                    perMinute.get(minute).busyPercent(), perMinute.get(minute).longestCycleMillis()) : "";
            text.append(String.format(Locale.ROOT, "  minute %d: tick average %.2f ms, max %.2f ms; %s%n", minute + 1,
                    average, max / 1e6, display));
        }
        text.append(String.format(Locale.ROOT, "  display thread (last minute): %.1f%% busy, longest cycle %.1f ms;"
                + " scoreboard refreshes skipped: %d%n", last.busyPercent(), last.longestCycleMillis(), skipped));
        text.append(String.format(Locale.ROOT, "  allocation (all threads): %.1f MB/s; GC: %d ms in %.0f s (%.2f%%), heap max %d MB%n",
                allocatedMb / wallSeconds, gcMillis, wallSeconds, gcMillis / (wallSeconds * 10),
                Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        return text.toString();
    }

    /** Spread out on a grid; {@code ring} keeps players, holograms and NPCs on different spots. */
    private static Pos spot(int index, int ring) {
        return DisplayTestServer.ORIGIN.add((index % 20 - 10) * 2.0, 0, (index / 20 - 5) * 2.0 + ring);
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getTotalThreadAllocatedBytes();
    }
}
