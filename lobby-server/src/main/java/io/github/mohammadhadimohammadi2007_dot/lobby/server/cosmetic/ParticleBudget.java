package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import java.util.HashMap;
import java.util.Map;

/**
 * How many cosmetic particle packets may still be sent in this tick: at most {@code perViewer} to any one player
 * and {@code perTick} in all. What does not fit is dropped, not delayed: a trail that skips a tick looks the
 * same, and a crowd at the spawn can never flood a client or the server's connection. Used by one thread.
 *
 * @param <V> who receives packets (a player in the lobby, anything comparable in tests)
 */
final class ParticleBudget<V> {

    private final Map<V, Integer> received = new HashMap<>();
    private int perViewer;
    private int perTick;
    private int sent;
    /** Read by other threads for statistics. */
    private volatile long dropped;

    /** Starts a new tick with these limits. */
    void reset(int perViewerLimit, int perTickLimit) {
        perViewer = perViewerLimit;
        perTick = perTickLimit;
        sent = 0;
        received.clear();
    }

    /** True (and counted) if one more packet may go to {@code viewer} in this tick. */
    boolean take(V viewer) {
        if (sent >= perTick) {
            dropped++;
            return false;
        }
        int count = received.getOrDefault(viewer, 0);
        if (count >= perViewer) {
            dropped++;
            return false;
        }
        received.put(viewer, count + 1);
        sent++;
        return true;
    }

    /** True if nothing more can be sent in this tick. */
    boolean exhausted() {
        return sent >= perTick;
    }

    /** Packets sent in this tick. */
    int sent() {
        return sent;
    }

    /** Packets dropped since the start, for {@code /setup-lobby info} and the load test. */
    long dropped() {
        return dropped;
    }
}
