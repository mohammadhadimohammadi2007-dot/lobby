package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilePvpStatsTest {

    @TempDir
    Path dir;

    @Test
    void keepsRecordsAndLeaderboards() throws Exception {
        PvpStatsContract.check(new FilePvpStats(dir));
    }

    @Test
    void recordsSurviveARestart() throws Exception {
        UUID steve = UUID.randomUUID();
        new FilePvpStats(dir).save(PvpStats.none(steve, "Steve").withKill("Steve"));
        assertEquals(1, new FilePvpStats(dir).load(steve).orElseThrow().kills());
    }
}