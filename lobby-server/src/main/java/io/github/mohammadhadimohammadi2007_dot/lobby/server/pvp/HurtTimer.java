package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

/**
 * Minecraft's short protection after a hit, the same in 1.8 and modern: for 10 ticks a player only takes the
 * part of a new hit that is stronger than the last one, and is not knocked back again. Without it, a fast
 * clicker would hit many times per second.
 */
final class HurtTimer {

    /** 10 ticks. */
    static final long PROTECTED_MILLIS = 500;

    private long lastHitMillis = Long.MIN_VALUE / 2;
    private double lastDamage;

    /** What a hit at {@code nowMillis} does. */
    record Result(double damage, boolean knockback) {
        static final Result NONE = new Result(0, false);
    }

    /** Applies a hit and returns how much of it counts. */
    synchronized Result hit(long nowMillis, double damage) {
        if (nowMillis - lastHitMillis < PROTECTED_MILLIS) {
            if (damage <= lastDamage) {
                return Result.NONE;
            }
            double extra = damage - lastDamage;
            lastDamage = damage;
            return new Result(extra, false);
        }
        lastHitMillis = nowMillis;
        lastDamage = damage;
        return new Result(damage, true);
    }

    /** Forgets the last hit (after a respawn). */
    synchronized void reset() {
        lastHitMillis = Long.MIN_VALUE / 2;
        lastDamage = 0;
    }
}
