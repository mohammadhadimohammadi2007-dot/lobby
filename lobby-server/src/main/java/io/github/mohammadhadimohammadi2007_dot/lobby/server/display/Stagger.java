package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import java.util.UUID;

/**
 * Spreads per-player work over its interval. Every player has a fixed offset, so with a line that updates
 * every second, a tenth of the players are done in each of the ten refreshes of that second instead of
 * all of them in one. Each player still gets exactly one update per interval.
 */
final class Stagger {

    private Stagger() {
    }

    /** The player's fixed offset within {@code interval}, in ticks. */
    static int phase(UUID player, int interval) {
        return Math.floorMod(player.hashCode(), interval);
    }

    /** True if this player's turn within {@code interval} lies in {@code (previous, now]}. */
    static boolean due(long previous, long now, int interval, UUID player) {
        int phase = phase(player, interval);
        return due(previous + phase, now + phase, interval);
    }

    /** True if a multiple of {@code interval} lies in {@code (previous, now]}. */
    static boolean due(long previous, long now, int interval) {
        return Math.floorDiv(now, interval) != Math.floorDiv(previous, interval);
    }

    /** Which of {@code count} rotating items this player sees now, with their offset. */
    static int index(long now, int interval, int count, UUID player) {
        return (int) Math.floorMod(Math.floorDiv(now + phase(player, interval), interval), count);
    }
}
