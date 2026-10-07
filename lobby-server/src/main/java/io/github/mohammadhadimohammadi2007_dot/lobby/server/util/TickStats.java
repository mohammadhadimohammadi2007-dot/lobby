package io.github.mohammadhadimohammadi2007_dot.lobby.server.util;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerTickMonitorEvent;

/** Measures ticks per second (TPS) and milliseconds per tick (MSPT) for {@code /lobby info}. */
public final class TickStats {

    /** Number of recent ticks the averages cover (5 seconds at 20 TPS). */
    private static final int WINDOW = 100;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long[] tickTimestamps = new long[WINDOW];
    private final double[] tickMillis = new double[WINDOW];
    private int index;
    private int filled;

    /** Starts listening to Minestom's tick monitor. */
    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(ServerTickMonitorEvent.class, this::onTick);
    }

    private synchronized void onTick(ServerTickMonitorEvent event) {
        tickTimestamps[index] = System.nanoTime();
        tickMillis[index] = event.getTickMonitor().getTickTime();
        index = (index + 1) % WINDOW;
        filled = Math.min(filled + 1, WINDOW);
    }

    /** Average ticks per second over the last few seconds, capped at the server's target. */
    public synchronized double tps() {
        if (filled < 2) {
            return MinecraftServer.TICK_PER_SECOND;
        }
        int newest = (index - 1 + WINDOW) % WINDOW;
        int oldest = filled < WINDOW ? 0 : index;
        double seconds = (tickTimestamps[newest] - tickTimestamps[oldest]) / NANOS_PER_SECOND;
        if (seconds <= 0) {
            return MinecraftServer.TICK_PER_SECOND;
        }
        return Math.min(MinecraftServer.TICK_PER_SECOND, (filled - 1) / seconds);
    }

    /** Average time one tick took, in milliseconds. */
    public synchronized double mspt() {
        if (filled == 0) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < filled; i++) {
            total += tickMillis[i];
        }
        return total / filled;
    }
}
