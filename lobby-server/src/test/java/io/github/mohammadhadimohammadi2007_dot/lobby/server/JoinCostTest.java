package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramType;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerSkinInitEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one join costs the tick thread in a full lobby: the real lobby (every listener, command and feature
 * as {@link LobbyServer} wires them, with the bundled configuration), 50 holograms, 30 NPCs and 199 players
 * walking, a quarter of them on 1.8 and a quarter on 1.20.1. Then 40 players join one at a time, each
 * leaving again two seconds later, so the lobby stays at 200.
 *
 * <p>A join is measured in two parts that a real server runs in the same tick: Minestom letting the player
 * in ({@code ConnectionManager.updateWaitingPlayers}: join packets, commands, the tab list, spawning and the
 * {@code PlayerSpawnEvent} listeners), and the rest of that tick. The ticks after it are reported too, since
 * work can be spread over them. The configuration phase before it runs on the player's own virtual thread
 * and is not counted.
 *
 * <p>Off by default: {@code LOBBY_LOAD_TEST=1 ./gradlew :lobby-server:test --tests '*JoinCostTest'}. With
 * {@code LOBBY_JFR=<file>} the run is also recorded with Java Flight Recorder; the times of each join are
 * marked in the recording as {@code lobby.Join} events. {@code LOBBY_JOINS} and {@code LOBBY_JOIN_GAP_TICKS}
 * change the number of joins and the ticks between them, for more profiling samples.
 */
@EnvTest
@EnabledIfEnvironmentVariable(named = "LOBBY_LOAD_TEST", matches = "1")
class JoinCostTest {

    private static final int PLAYERS = 200;
    private static final int HOLOGRAMS = 50;
    private static final int NPCS = 30;
    private static final int JOINS = Integer.parseInt(System.getenv().getOrDefault("LOBBY_JOINS", "40"));
    private static final int TICKS_BETWEEN_JOINS = Integer.parseInt(System.getenv().getOrDefault("LOBBY_JOIN_GAP_TICKS", "40"));
    private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final double CIRCLE_RADIUS = 6;
    private static final int MID_PROTOCOL = 763;

    @TempDir
    Path dir;

    private int tick;
    private long nextTick;

    @Test
    void joiningAFullLobby(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = new LobbyServer(config);
        lobby.build();
        try {
            measure(env, lobby, SpawnListener.spawnPosition(config.current().config()));
        } finally {
            lobby.stopFeatures();
        }
    }

    private void measure(Env env, LobbyServer lobby, Pos spawn) throws Exception {
        Instance map = lobby.world().instance();
        for (int i = 0; i < HOLOGRAMS; i++) {
            String line = i % 5 == 0 ? "<gray>Hi %player_name%, ping %player_ping%" : "<gold>%server_online% online";
            lobby.holograms().create("h" + i, HologramType.TEXT, spot(spawn, i, 12), List.of(line, "<gray>Lobby"));
        }
        for (int i = 0; i < NPCS; i++) {
            NpcData npc = lobby.npcs().create("n" + i, spot(spawn, i, 16));
            npc.nameTag().frames(List.of(List.of(i % 3 == 0 ? "<aqua>Hello %player_name%" : "<yellow>Game " + i)));
            npc.turnToPlayer(i % 2 == 0);
            lobby.npcs().changed(npc);
        }
        AtomicLong letInAt = new AtomicLong();
        // Fired at the start of Player#UNSAFE_init, which updateWaitingPlayers calls on the tick thread.
        env.process().eventHandler().addListener(PlayerSkinInitEvent.class, event -> letInAt.set(System.nanoTime()));

        List<Player> players = new ArrayList<>();
        nextTick = System.nanoTime();
        // Filling up from a fresh start: the first joins also load and compile the code they use.
        List<String> fillSlowest = new ArrayList<>();
        long[] fill = new long[PLAYERS - 1];
        for (int i = 0; i < PLAYERS - 1; i++) {
            long gcBefore = gcMillis();
            JoinEvent letInEvent = JoinEvent.begin(i == 0 ? "first" : "fill", "let-in");
            players.add(join(env, lobby, map, spawn, "P" + i, i));
            long letInEnd = System.nanoTime();
            letInEvent.commit();
            JoinEvent tickEvent = JoinEvent.begin(i == 0 ? "first" : "fill", "tick");
            fill[i] = letInEnd - letInAt.get() + walkAndTick(env, players, spawn);
            tickEvent.commit();
            if (fill[i] > TICK_NANOS) {
                fillSlowest.add(String.format(Locale.ROOT, "join %d: %.1f ms (GC %d ms)", i + 1, fill[i] / 1e6,
                        gcMillis() - gcBefore));
            }
        }
        for (int i = 0; i < 20 * 10; i++) {
            walkAndTick(env, players, spawn);
        }

        long[] letIn = new long[JOINS];
        long[] joinTick = new long[JOINS];
        long[] after = new long[JOINS];
        long[] leave = new long[JOINS];
        for (int j = 0; j < JOINS; j++) {
            JoinEvent letInEvent = JoinEvent.begin("full", "let-in");
            Player joined = join(env, lobby, map, spawn, "J" + j, j);
            long letInEnd = System.nanoTime();
            letInEvent.commit();
            letIn[j] = letInEnd - letInAt.get();
            players.add(joined);
            JoinEvent tickEvent = JoinEvent.begin("full", "tick");
            joinTick[j] = walkAndTick(env, players, spawn);
            tickEvent.commit();
            for (int t = 1; t < TICKS_BETWEEN_JOINS; t++) {
                after[j] = Math.max(after[j], walkAndTick(env, players, spawn));
            }
            players.remove(joined);
            joined.kick("bye");
            leave[j] = walkAndTick(env, players, spawn);
        }

        long[] total = new long[JOINS];
        for (int j = 0; j < JOINS; j++) {
            total[j] = letIn[j] + joinTick[j];
        }
        String report = String.format(Locale.ROOT,
                "filling up from a fresh start to %d: join tick %s; over 50 ms: %s%n"
                        + "join cost with %d online (real lobby, %d holograms, %d NPCs, mixed client versions), %d joins:%n"
                        + "  letting the player in (updateWaitingPlayers): %s%n"
                        + "  rest of that tick: %s%n"
                        + "  join tick in total: %s%n"
                        + "  longest of the %d ticks after: %s%n"
                        + "  leave tick: %s%n",
                PLAYERS - 1, stats(fill), fillSlowest.isEmpty() ? "none" : String.join("; ", fillSlowest),
                PLAYERS, HOLOGRAMS, NPCS, JOINS, stats(letIn), stats(joinTick), stats(total), TICKS_BETWEEN_JOINS - 1,
                stats(after), stats(leave));
        System.out.println(report);
        Path reports = Path.of("build", "reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("join-cost.txt"), report);
        double median = median(total) / 1e6;
        assertTrue(median < 100, "median join tick " + median + " ms");
    }

    /** Marks a join in a Java Flight Recorder recording. */
    @jdk.jfr.Name("lobby.Join")
    @jdk.jfr.Label("Lobby join")
    static final class JoinEvent extends jdk.jfr.Event {
        @jdk.jfr.Label("Phase")
        String phase;
        @jdk.jfr.Label("Part")
        String part;

        static JoinEvent begin(String phase, String part) {
            JoinEvent event = new JoinEvent();
            event.phase = phase;
            event.part = part;
            event.begin();
            return event;
        }
    }

    private static Player join(Env env, LobbyServer lobby, Instance map, Pos spawn, String name, int index) {
        UUID id = UUID.randomUUID();
        int protocol = switch (index % 4) {
            case 0 -> ProtocolVersions.V1_8;
            case 1 -> MID_PROTOCOL;
            default -> MinecraftServer.PROTOCOL_VERSION;
        };
        // The bridge reports the version right after the player arrives on the proxy, before the lobby.
        BridgeTestAccess.reportVersion(lobby.bridge(), id, protocol);
        // Everyone appears at the spawn point, as in the lobby; the walking then takes them to their own spot.
        return env.createConnection(new GameProfile(id, name)).connect(map, spawn);
    }

    /** One tick at 20 ticks a second with every player walking a small circle; returns how long it took. */
    private long walkAndTick(Env env, List<Player> players, Pos spawn) throws InterruptedException {
        for (int i = 0; i < players.size(); i++) {
            Pos centre = spot(spawn, i, 4);
            double angle = (tick + i * 7) * 0.05;
            Pos to = centre.add(Math.cos(angle) * CIRCLE_RADIUS, 0, Math.sin(angle) * CIRCLE_RADIUS)
                    .withView((float) Math.toDegrees(angle), 0);
            players.get(i).addPacketToQueue(new ClientPlayerPositionAndRotationPacket(to, true, false));
        }
        long start = System.nanoTime();
        env.tick();
        long took = System.nanoTime() - start;
        tick++;
        nextTick += TICK_NANOS;
        long sleep = nextTick - System.nanoTime();
        if (sleep > 0) {
            TimeUnit.NANOSECONDS.sleep(sleep);
        } else {
            nextTick = System.nanoTime();
        }
        return took;
    }

    private static Pos spot(Pos spawn, int index, int ring) {
        return spawn.add((index % 20 - 10) * 2.0, 0, (index / 20 - 5) * 2.0 + ring);
    }

    private static long gcMillis() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static String stats(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return String.format(Locale.ROOT, "median %.1f ms, p90 %.1f, max %.1f", median(sorted) / 1e6,
                sorted[(int) (sorted.length * 0.9)] / 1e6, sorted[sorted.length - 1] / 1e6);
    }

    private static double median(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
