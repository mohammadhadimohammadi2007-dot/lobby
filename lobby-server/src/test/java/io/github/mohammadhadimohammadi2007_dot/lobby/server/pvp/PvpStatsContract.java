package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What every PvP stats store must do; run against the file and the database. */
final class PvpStatsContract {

    private PvpStatsContract() {
    }

    static void check(PvpStatsStore store) throws Exception {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID ali = UUID.randomUUID();
        assertEquals(Optional.empty(), store.load(steve));

        PvpStats steveStats = PvpStats.none(steve, "Steve").withKill("Steve").withKill("Steve").withKill("Steve");
        store.save(steveStats);
        store.save(PvpStats.none(alex, "Alex").withKill("Alex").withDeath("Alex"));
        store.save(PvpStats.none(ali, "Ali").withDeath("Ali"));
        assertEquals(Optional.of(steveStats), store.load(steve));

        assertEquals(List.of("Steve", "Alex"), names(store.top(PvpStats.Board.KILLS, 10)));
        assertEquals(List.of("Steve"), names(store.top(PvpStats.Board.KILLS, 1)));
        assertEquals(List.of("Alex", "Ali"), names(store.top(PvpStats.Board.DEATHS, 10)), "same count: by name");
        assertEquals(List.of("Steve", "Alex"), names(store.top(PvpStats.Board.BEST_STREAK, 10)));
        assertEquals(List.of("Steve"), names(store.top(PvpStats.Board.STREAK, 10)), "Alex's streak ended, 0 is left out");

        PvpStats renamed = steveStats.withDeath("Steve2");
        store.save(renamed);
        assertEquals(Optional.of(renamed), store.load(steve), "saved again over the old record");
        assertEquals(3, store.load(steve).orElseThrow().bestStreak());
    }

    private static List<String> names(List<PvpStats> stats) {
        return stats.stream().map(PvpStats::name).toList();
    }
}