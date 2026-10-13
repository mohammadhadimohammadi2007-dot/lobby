package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Players in combat in a lobby's PvP area, as the lobbies report them: until when, on which lobby, and what to
 * tell them when they try to switch servers. Times are the proxy's own clock (the lobby sends how long, not
 * until when), so the two machines' clocks do not need to agree.
 */
final class ProxyCombatTags {

    /** One player's combat. */
    record Tag(String lobby, long untilMillis, String message) {
    }

    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    ProxyCombatTags(LongSupplier clock) {
        this.clock = clock;
    }

    /** A lobby reported a combat tag; 0 ends it. */
    void update(UUID player, String lobby, long remainingMillis, String message) {
        if (remainingMillis <= 0) {
            tags.remove(player);
        } else {
            tags.put(player, new Tag(lobby, clock.getAsLong() + remainingMillis, message));
        }
    }

    /**
     * The tag that stops {@code player}, who is on {@code currentServer}, from switching now, or {@code null}.
     * A tag only counts while the player is still on the lobby that reported it.
     */
    Tag blocking(UUID player, String currentServer) {
        Tag tag = tags.get(player);
        if (tag == null) {
            return null;
        }
        if (tag.untilMillis() <= clock.getAsLong() || !tag.lobby().equals(currentServer)) {
            tags.remove(player, tag);
            return null;
        }
        return tag;
    }

    /** Whole seconds left, rounded up. */
    long secondsLeft(Tag tag) {
        return Math.max(0, (tag.untilMillis() - clock.getAsLong() + 999) / 1000);
    }

    void forget(UUID player) {
        tags.remove(player);
    }
}