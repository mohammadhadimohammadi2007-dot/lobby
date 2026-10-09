package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The busy share and longest cycle of the display thread, over the last minute. */
class DisplayLoadTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long MILLI = 1_000_000L;

    @Test
    void busyShareOfTheTimeSinceStart() {
        DisplayLoad load = new DisplayLoad(0);
        load.record(0, 100 * MILLI);
        load.record(SECOND, 50 * MILLI);

        DisplayLoad.Snapshot snapshot = load.snapshot(2 * SECOND);

        // 150 ms of work in the first 2 seconds.
        assertEquals(7.5, snapshot.busyPercent(), 1e-9);
        assertEquals(100, snapshot.longestCycleMillis(), 1e-9);
        assertEquals(2, snapshot.cycles());
    }

    @Test
    void onlyTheLastMinuteCounts() {
        DisplayLoad load = new DisplayLoad(0);
        load.record(0, 500 * MILLI);
        load.record(70 * SECOND, 30 * MILLI);

        DisplayLoad.Snapshot snapshot = load.snapshot(100 * SECOND);

        // The 500 ms cycle ended 99.5 s ago, so it is out of the window and no longer the longest.
        assertEquals(30, snapshot.longestCycleMillis(), 1e-9);
        assertEquals(1, snapshot.cycles());
        assertEquals(30.0 / 600, snapshot.busyPercent(), 1e-9);
    }

    @Test
    void aCycleAcrossTheWindowsEdgeCountsOnlyItsInsidePart() {
        DisplayLoad load = new DisplayLoad(0);
        // Runs from 39 s to 41 s; the window at 100 s starts at 40 s, so one second of it is inside.
        load.record(39 * SECOND, 2 * SECOND);

        DisplayLoad.Snapshot snapshot = load.snapshot(100 * SECOND);

        assertEquals(100.0 / 60, snapshot.busyPercent(), 1e-9);
        assertEquals(2000, snapshot.longestCycleMillis(), 1e-9);
    }
}
