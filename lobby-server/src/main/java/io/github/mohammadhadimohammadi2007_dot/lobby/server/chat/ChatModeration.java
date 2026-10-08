package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff-controlled chat state: lock, slow mode, chat spy, and mutes made by the auto-mute when there is no
 * bridge. Lives in memory; a restart clears it. Thread-safe.
 */
public final class ChatModeration {

    private volatile boolean locked;
    private volatile int slowmodeSeconds;
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> localMutes = new ConcurrentHashMap<>();

    public boolean locked() {
        return locked;
    }

    public void locked(boolean value) {
        locked = value;
    }

    /** Seconds every player must wait between messages; 0 = off. */
    public int slowmodeSeconds() {
        return slowmodeSeconds;
    }

    public void slowmodeSeconds(int seconds) {
        slowmodeSeconds = Math.max(0, seconds);
    }

    /** Turns chat spy on or off for a staff member; returns the new state. */
    public boolean toggleSpy(UUID staff) {
        if (spies.remove(staff)) {
            return false;
        }
        spies.add(staff);
        return true;
    }

    public boolean spying(UUID staff) {
        return spies.contains(staff);
    }

    /** Mutes a player on this lobby until {@code untilMillis}. */
    public void muteLocally(UUID player, long untilMillis) {
        localMutes.put(player, untilMillis);
    }

    /** When the local mute ends, or 0 if the player is not muted locally. */
    public long localMuteUntil(UUID player) {
        Long until = localMutes.get(player);
        if (until == null) {
            return 0;
        }
        if (until <= System.currentTimeMillis()) {
            localMutes.remove(player, until);
            return 0;
        }
        return until;
    }

    /** Forgets a player who left (local mutes are kept until they expire). */
    public void forget(UUID player) {
        spies.remove(player);
    }
}
