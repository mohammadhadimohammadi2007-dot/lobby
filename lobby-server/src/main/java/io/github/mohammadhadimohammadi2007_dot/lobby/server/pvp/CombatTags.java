package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is in combat and until when. A hit tags the attacker and the defender; while tagged a player cannot
 * leave the PvP area, switch servers or lobbies, or use the commands pvp.yml lists. Thread-safe; times are
 * passed in, so it is tested without waiting.
 */
public final class CombatTags {

    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    /**
     * Tags (or tags again) a player until {@code nowMillis + durationMillis}.
     *
     * @return true if the player was not tagged before
     */
    public boolean tag(UUID player, long nowMillis, long durationMillis) {
        Long before = until.put(player, nowMillis + durationMillis);
        return before == null || before <= nowMillis;
    }

    /** Milliseconds of combat left, 0 if not tagged. */
    public long remainingMillis(UUID player, long nowMillis) {
        Long end = until.get(player);
        return end == null ? 0 : Math.max(0, end - nowMillis);
    }

    /** Whole seconds left, rounded up, for messages: 0 if not tagged. */
    public long remainingSeconds(UUID player, long nowMillis) {
        return (remainingMillis(player, nowMillis) + 999) / 1000;
    }

    public boolean tagged(UUID player, long nowMillis) {
        return remainingMillis(player, nowMillis) > 0;
    }

    /** Ends a player's combat (death, quit). True if they were tagged. */
    public boolean clear(UUID player, long nowMillis) {
        Long end = until.remove(player);
        return end != null && end > nowMillis;
    }

    /** Removes and returns the players whose combat ended by {@code nowMillis}. */
    public Set<UUID> expire(long nowMillis) {
        Set<UUID> ended = ConcurrentHashMap.newKeySet();
        until.entrySet().removeIf(entry -> {
            if (entry.getValue() <= nowMillis) {
                ended.add(entry.getKey());
                return true;
            }
            return false;
        });
        return ended;
    }

    /** The players tagged right now. */
    public Set<UUID> tagged() {
        return Set.copyOf(until.keySet());
    }
}
