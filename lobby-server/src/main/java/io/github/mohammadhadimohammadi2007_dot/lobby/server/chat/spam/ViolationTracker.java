package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Violation points per player. Each filter hit adds points; points slowly go away again
 * ({@code decayPerMinute}), so only repeated bad behavior reaches the auto-mute threshold.
 */
public final class ViolationTracker {

    private static final double NANOS_PER_MINUTE = 60_000_000_000.0;

    private final Map<UUID, Points> points = new ConcurrentHashMap<>();

    private record Points(double value, long updatedNanos) {
    }

    /**
     * Adds points and returns the player's new total.
     *
     * @param decayPerMinute points removed per minute since the last change
     */
    public double add(UUID player, double amount, double decayPerMinute, long nowNanos) {
        Points updated = points.compute(player, (id, current) -> {
            double value = current == null ? 0 : decayed(current, decayPerMinute, nowNanos);
            return new Points(value + amount, nowNanos);
        });
        return updated.value();
    }

    /** The player's current total after decay. */
    public double current(UUID player, double decayPerMinute, long nowNanos) {
        Points current = points.get(player);
        return current == null ? 0 : decayed(current, decayPerMinute, nowNanos);
    }

    /** Clears a player's points (after an auto-mute, or when they leave). */
    public void reset(UUID player) {
        points.remove(player);
    }

    private static double decayed(Points current, double decayPerMinute, long nowNanos) {
        double minutes = (nowNanos - current.updatedNanos()) / NANOS_PER_MINUTE;
        return Math.max(0, current.value() - minutes * decayPerMinute);
    }
}
