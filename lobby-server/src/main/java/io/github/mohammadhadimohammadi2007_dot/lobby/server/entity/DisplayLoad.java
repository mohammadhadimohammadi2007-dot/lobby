package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * How busy the display thread is: the share of the last minute it spent working, and its longest single
 * refresh in that minute. Holograms, NPCs and later the scoreboard and tab list all run on that one
 * thread, so this is the number that says when it needs splitting.
 *
 * <p>Thread-safe; recording costs one deque operation per refresh.
 */
public final class DisplayLoad {

    /** The window the numbers are about. */
    public static final long WINDOW_NANOS = 60_000_000_000L;
    private static final double PERCENT = 100.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    /** One piece of work: when it started and how long it took. */
    private record Cycle(long startNanos, long durationNanos) {
    }

    /**
     * The numbers for {@code /lobby info}.
     *
     * @param busyPercent       share of the window spent working, 0 to 100
     * @param longestCycleMillis the longest single cycle in the window
     * @param cycles            how many cycles ran in the window
     */
    public record Snapshot(double busyPercent, double longestCycleMillis, int cycles) {
    }

    private final Deque<Cycle> cycles = new ArrayDeque<>();
    private final long startedNanos;

    public DisplayLoad() {
        this(System.nanoTime());
    }

    /** For tests: as if the thread started at {@code startedNanos}. */
    DisplayLoad(long startedNanos) {
        this.startedNanos = startedNanos;
    }

    /** Records one finished cycle. */
    public synchronized void record(long startNanos, long durationNanos) {
        cycles.addLast(new Cycle(startNanos, durationNanos));
        prune(startNanos + durationNanos);
    }

    /** The numbers for the last minute, or for the time since start if that is shorter. */
    public Snapshot snapshot() {
        return snapshot(System.nanoTime());
    }

    synchronized Snapshot snapshot(long nowNanos) {
        prune(nowNanos);
        long windowStart = Math.max(startedNanos, nowNanos - WINDOW_NANOS);
        long window = Math.max(1, nowNanos - windowStart);
        long busy = 0;
        long longest = 0;
        for (Cycle cycle : cycles) {
            // Only the part of a cycle that lies inside the window counts.
            long start = Math.max(cycle.startNanos(), windowStart);
            long end = cycle.startNanos() + cycle.durationNanos();
            busy += Math.max(0, end - start);
            longest = Math.max(longest, cycle.durationNanos());
        }
        return new Snapshot(Math.min(PERCENT, busy * PERCENT / window), longest / NANOS_PER_MILLI, cycles.size());
    }

    private void prune(long nowNanos) {
        long oldest = nowNanos - WINDOW_NANOS;
        while (!cycles.isEmpty() && cycles.peekFirst().startNanos() + cycles.peekFirst().durationNanos() < oldest) {
            cycles.removeFirst();
        }
    }
}
