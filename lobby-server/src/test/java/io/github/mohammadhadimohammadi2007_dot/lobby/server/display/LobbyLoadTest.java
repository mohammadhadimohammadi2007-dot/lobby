package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.PerfReport;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic.CosmeticType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic.CosmeticsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.DisplayLoad;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.MeasuredThread;
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
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.FilePvpStats;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.Kit;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.PvpService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.PvpStatsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
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
 * nametags and boss bar, hotbar items, player visibility, regions, jump pads and portals all running. 20 of the
 * players are in the PvP area instead, each hitting another about twice a second (kills, respawns and combat tags
 * included). Every walking player has a trail, every other one a hat, every third a pet, every fourth a balloon
 * and every fifth an outfit (LOBBY_LOAD_TEST_COSMETICS=0 turns that off, for comparison).
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
    /** Players 0 to 19 fight in the PvP area instead of walking. */
    private static final int FIGHTERS = 20;
    /** Ticks between two hits of the same fighter. */
    private static final int HIT_EVERY_TICKS = 10;
    private static final Pos ARENA = DisplayTestServer.ORIGIN.add(0, 0, 60);
    /** LOBBY_LOAD_TEST_PVP=0 runs the same test without the PvP area, for comparison. */
    private static final boolean PVP = !"0".equals(System.getenv("LOBBY_LOAD_TEST_PVP"));
    /** LOBBY_LOAD_TEST_COSMETICS=0 runs the same test without cosmetics, for comparison. */
    private static final boolean COSMETICS = !"0".equals(System.getenv("LOBBY_LOAD_TEST_COSMETICS"));
    /** One trail of each style, a hat, a pet, a balloon and an outfit, free for everyone. */
    private static final String LOAD_COSMETICS = """
            trails:
              feet:
                particle: flame
                style: feet
                amount: 3
                permission: ""
              halo:
                particle: heart
                style: halo
                permission: ""
              spiral:
                particle: note
                style: spiral
                permission: ""
              ring:
                particle: witch
                style: ring
                permission: ""
              wings:
                particle: dust
                color: "#ffffff"
                style: wings
                permission: ""
              cloud:
                particle: cloud
                drip: dripping_water
                style: cloud
                permission: ""
            hats:
              glass:
                material: glass
                permission: ""
            gadgets:
              leap:
                type: leap
                permission: ""
            pets:
              dog:
                entity: wolf
                name-tag: "%player_name%'s dog"
                permission: ""
            balloons:
              red:
                material: red_wool
                permission: ""
            outfits:
              red:
                chest: leather_chestplate
                legs: leather_leggings
                feet: leather_boots
                color: "#cc3333"
                permission: ""
            """;
    private static final List<String> TRAILS = List.of("feet", "halo", "spiral", "ring", "wings", "cloud");

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
        if (COSMETICS) {
            Files.writeString(dir.resolve(ConfigManager.COSMETICS_FILE), LOAD_COSMETICS);
        }
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
        PortalService portals = PortalService.start(dir, regions, actions, server.text, new io.github.mohammadhadimohammadi2007_dot.lobby.server.setup.SetupWand(server.text, regions, name -> java.util.Optional.empty(), new io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.MeasuredThread("test-effects")));
        cleanup.add(portals::shutdown);
        EventNode<PlayerEvent> node = EventNode.type("load-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        server.renderer.register(node);
        server.displays.register(node);
        hotbar.register(node);
        PvpService pvp = PvpService.start(dir, server.config, server.text, bridge, regions,
                new PvpStatsService(new FilePvpStats(dir), System::currentTimeMillis), actions, System::currentTimeMillis);
        cleanup.add(pvp::shutdown);
        pvp.hooks(new PvpService.Hooks(hotbar::give, movement::ground, visibility::recheck));
        hotbar.exempt(pvp::inArea);
        movement.grounded(pvp::inArea);
        visibility.alwaysVisible((viewer, target) -> pvp.inArea(viewer) && pvp.inArea(target));
        MeasuredThread effects = new MeasuredThread("load-effects");
        cleanup.add(effects::shutdown);
        CosmeticsService cosmetics = new CosmeticsService(new CosmeticsService.Dependencies(server.config, server.text,
                server.permissions, settings, DisplayTestServer::protocolOf, visibility::canSee, effects,
                System::currentTimeMillis));
        cleanup.add(0, cosmetics::shutdown);
        cosmetics.suppressWhen(pvp::inArea);
        cosmetics.onItemsChanged(hotbar::give);
        hotbar.extras(cosmetics.hotbarExtras());
        cosmetics.register(node);
        if (PVP) {
            pvp.setRegion(new Region("wand", "selection", ARENA.blockX() - 12, ARENA.blockY() - 2, ARENA.blockZ() - 12,
                    ARENA.blockX() + 12, ARENA.blockY() + 6, ARENA.blockZ() + 12));
            pvp.setKit(new Kit(java.util.Map.of(0, ItemStack.of(Material.IRON_SWORD)),
                    java.util.Map.of(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.IRON_CHESTPLATE))));
        }
        pvp.register(env.process().eventHandler(), node);
        regions.register(node);
        movement.register(node);

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
            players.add(env.createConnection(new GameProfile(id, "P" + tier + i)).connect(map, home(i)));
            if (COSMETICS && i >= FIGHTERS) {
                // Every walking player has a trail (the styles in turn), every other one a hat, every third a pet,
                // every fourth a balloon and every fifth an outfit.
                Player joined = players.getLast();
                cosmetics.equip(joined, cosmetics.config().find(CosmeticType.TRAIL, TRAILS.get(i % TRAILS.size())));
                if (i % 2 == 0) {
                    cosmetics.equip(joined, cosmetics.config().find(CosmeticType.HAT, "glass"));
                }
                if (i % 3 == 0) {
                    cosmetics.equip(joined, cosmetics.config().find(CosmeticType.PET, "dog"));
                }
                if (i % 4 == 0) {
                    cosmetics.equip(joined, cosmetics.config().find(CosmeticType.BALLOON, "red"));
                }
                if (i % 5 == 0) {
                    cosmetics.equip(joined, cosmetics.config().find(CosmeticType.OUTFIT, "red"));
                }
            }
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
        int skippedBefore = server.displays.skippedRefreshes();
        long gcBefore = gcMillis();
        long allocatedBefore = allocated();
        long start = System.nanoTime();
        List<Long> ticks = new ArrayList<>();
        List<DisplayLoad.Snapshot> displayPerMinute = new ArrayList<>();
        List<DisplayLoad.Snapshot> boardPerMinute = new ArrayList<>();
        List<DisplayLoad.Snapshot> effectsPerMinute = new ArrayList<>();
        long droppedBefore = cosmetics.droppedParticles();
        long nextTick = System.nanoTime();
        long end = start + TimeUnit.SECONDS.toNanos(seconds);
        int tick = 0;
        while (System.nanoTime() < end) {
            walk(players, tick);
            fight(players, tick);
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
                boardPerMinute.add(server.displays.load());
                effectsPerMinute.add(effects.load());
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

        String report = report(ticks, displayPerMinute, boardPerMinute, server.renderer.load(), server.displays.load(),
                wallSeconds, gc, allocatedMb, server.displays.skippedRefreshes() - skippedBefore, skippedBefore)
                + String.format(Locale.ROOT, "  PvP: %d of %d fighters in the area at the end, %d kills%n", pvp.playersInArea(),
                FIGHTERS, players.subList(0, FIGHTERS).stream()
                        .mapToInt(player -> pvp.stats().get(player.getUuid(), player.getUsername()).kills()).sum())
                + effectsReport(effectsPerMinute, effects.load(), cosmetics.droppedParticles() - droppedBefore);
        System.out.println(report);
        System.out.println("  ticks over 50 ms: " + spikes.size() + (spikes.isEmpty() ? "" : " - " + String.join("; ",
                spikes.subList(0, Math.min(10, spikes.size())))));
        Path out = Path.of("build", "reports", "lobby-load-test.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        double average = ticks.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        PerfReport.report("LobbyLoadTest: average tick (MSPT)", average, 25, "ms");
    }

    /** The cosmetics part of the report: the effects thread per minute, and particles the limits dropped. */
    private static String effectsReport(List<DisplayLoad.Snapshot> perMinute, DisplayLoad.Snapshot last, long dropped) {
        if (!COSMETICS) {
            return "  cosmetics: off (LOBBY_LOAD_TEST_COSMETICS=0)\n";
        }
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "  cosmetics: %d walking players with a"
                + " trail, half with a hat, a third with a pet, a quarter with a balloon, a fifth with an outfit;"
                + " effects thread last minute %.1f%% busy, longest cycle %.1f ms; %d particle packets dropped by the"
                + " limits%n", PLAYERS - FIGHTERS, last.busyPercent(), last.longestCycleMillis(), dropped));
        for (int minute = 0; minute < perMinute.size(); minute++) {
            text.append(String.format(Locale.ROOT, "    minute %d: effects thread %.1f%% busy, longest %.1f ms%n",
                    minute + 1, perMinute.get(minute).busyPercent(), perMinute.get(minute).longestCycleMillis()));
        }
        return text.toString();
    }

    /** Where a player joins and walks around: fighters in the PvP area, everyone else on the grid. */
    private static Pos home(int index) {
        if (index < FIGHTERS) {
            return ARENA.add((index % 4 - 1.5) * 3.0, 0, (index / 4 - 2) * 3.0);
        }
        return spot(index, 4);
    }

    /** Fighters fight in pairs (0 and 1, 2 and 3...), each hitting every {@link #HIT_EVERY_TICKS} ticks. */
    private static void fight(List<Player> players, int tick) {
        for (int i = 0; i < Math.min(FIGHTERS, players.size()); i++) {
            if ((tick + i) % HIT_EVERY_TICKS == 0) {
                EventDispatcher.call(new EntityAttackEvent(players.get(i), players.get(i ^ 1)));
            }
        }
    }

    /** Every player walks a small circle around their own spot, one step per tick, looking ahead. */
    private static void walk(List<Player> players, int tick) {
        for (int i = 0; i < players.size(); i++) {
            Pos centre = home(i);
            double angle = (tick + i * 7) * 0.05;
            // Fighters move a little on their spot, staying close to the one they hit.
            double radius = i < FIGHTERS ? 1 : CIRCLE_RADIUS;
            Pos to = centre.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius)
                    .withView((float) Math.toDegrees(angle), 0);
            players.get(i).addPacketToQueue(new ClientPlayerPositionAndRotationPacket(to, true, false));
        }
    }

    private static String report(List<Long> ticks, List<DisplayLoad.Snapshot> perMinute,
                                 List<DisplayLoad.Snapshot> boardPerMinute, DisplayLoad.Snapshot last,
                                 DisplayLoad.Snapshot boardLast, double wallSeconds, long gcMillis, double allocatedMb,
                                 int skipped, int skippedFilling) {
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
            String display = minute < perMinute.size() ? String.format(Locale.ROOT,
                    "holograms/NPCs thread %.1f%% busy, longest %.1f ms; scoreboard/tab thread %.1f%% busy, longest %.1f ms",
                    perMinute.get(minute).busyPercent(), perMinute.get(minute).longestCycleMillis(),
                    boardPerMinute.get(minute).busyPercent(), boardPerMinute.get(minute).longestCycleMillis()) : "";
            text.append(String.format(Locale.ROOT, "  minute %d: tick average %.2f ms, max %.2f ms; %s%n", minute + 1,
                    average, max / 1e6, display));
        }
        text.append(String.format(Locale.ROOT, "  last minute: holograms/NPCs thread %.1f%% busy, longest cycle %.1f ms;"
                        + " scoreboard/tab thread %.1f%% busy, longest cycle %.1f ms%n", last.busyPercent(),
                last.longestCycleMillis(), boardLast.busyPercent(), boardLast.longestCycleMillis()));
        text.append(String.format(Locale.ROOT, "  scoreboard refreshes skipped: %d in the measured %.0f s (%d while filling"
                + " up and warming up)%n", skipped, wallSeconds, skippedFilling));
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
