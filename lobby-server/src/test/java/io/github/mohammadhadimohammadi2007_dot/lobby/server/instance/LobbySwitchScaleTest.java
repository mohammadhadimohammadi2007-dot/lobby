package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What moving a full lobby costs. Changing instance has to happen on the tick thread, so 200 players
 * asking at once must not stall it: the moves are spread over ticks.
 */
@EnvTest
class LobbySwitchScaleTest {

    private static final Pos SPAWN = new Pos(0.5, 41, 0.5);
    private static final int PLAYERS = 200;
    /**
     * How much longer than an idle tick a typical tick may take while players are being moved. Measured
     * against an idle tick with the same players, because this test harness ticks 200 fake connections
     * on one thread and that alone costs more than a real server does.
     */
    private static final long MAX_EXTRA_MILLIS = 25;
    /** Anything near this means the moves are not being spread over ticks any more. */
    private static final long NEVER_THIS_SLOW_MILLIS = 150;
    /** The most players {@code LobbyInstances} could move in one tick on a very fast machine. */
    private static final int MOST_MOVES_PER_TICK = 32;

    @TempDir
    Path dir;

    private LobbyInstances lobbies(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Path file = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(file, Files.readString(file).replace("instances: 1", "instances: 2"));
        config.load();
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        return LobbyInstances.create((InstanceContainer) env.createFlatInstance(), config, text,
                new OperatorPermissionService(config));
    }

    @Test
    void movingAFullLobbyNeverStallsATick(Env env) throws Exception {
        LobbyInstances lobbies = lobbies(env);
        Instance first = lobbies.byNumber(1);
        List<Player> players = new ArrayList<>(PLAYERS);
        for (int number = 0; number < PLAYERS; number++) {
            players.add(env.createConnection(new GameProfile(UUID.randomUUID(), "p" + number))
                    .connect(first, SPAWN));
        }
        env.tick();

        // What a tick costs with 200 players and nothing happening, to compare against.
        long baselineMillis = 0;
        for (int tick = 0; tick < 10; tick++) {
            long start = System.nanoTime();
            env.tick();
            baselineMillis = Math.max(baselineMillis, (System.nanoTime() - start) / 1_000_000);
        }

        for (Player player : players) {
            lobbies.switchTo(player, 2);
        }

        List<Long> tickMillis = new ArrayList<>();
        // Tick until every player has arrived, measuring every tick on the way.
        while (lobbies.onlineIn(2) < PLAYERS && tickMillis.size() < 400) {
            long start = System.nanoTime();
            env.tick();
            tickMillis.add((System.nanoTime() - start) / 1_000_000);
        }
        int ticks = tickMillis.size();
        List<Long> sorted = tickMillis.stream().sorted().toList();
        long medianMillis = sorted.get(sorted.size() / 2);
        long worstTickMillis = sorted.getLast();

        System.out.println(PLAYERS + " players switching lobby: " + ticks + " tick(s), median tick "
                + medianMillis + " ms, worst tick " + worstTickMillis
                + " ms; worst idle tick with the same players: " + baselineMillis + " ms");
        assertEquals(PLAYERS, lobbies.onlineIn(2), "every player arrived");
        assertEquals(0, lobbies.onlineIn(1));
        assertTrue(ticks >= PLAYERS / MOST_MOVES_PER_TICK,
                "the moves must be spread over ticks, but took only " + ticks + " tick(s)");
        assertTrue(worstTickMillis < NEVER_THIS_SLOW_MILLIS,
                "a tick took " + worstTickMillis + " ms while moving " + PLAYERS + " players");
        // The median, not the worst: this harness ticks 200 fake connections on one thread and its own
        // garbage collection shows up in single ticks, which a real server does not share.
        assertTrue(medianMillis - baselineMillis < MAX_EXTRA_MILLIS,
                "moving players added " + (medianMillis - baselineMillis) + " ms to a typical tick");
    }
}
