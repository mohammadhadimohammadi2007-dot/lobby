package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam.TokenBucket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the chat remembers about each online player: when they joined, whether they moved, their rate limit,
 * their last messages and when they last mentioned someone. Created on join, removed on quit.
 */
public final class ChatPlayerTracker {

    /** Per-player state. Written by the chat thread, except {@code moved} (tick thread). */
    public static final class State {
        private final long joinedNanos;
        private volatile boolean moved;
        private TokenBucket bucket;
        private int cooldownSeconds = -1;
        private long lastSlowmodeMessageNanos;
        private long lastMentionNanos;
        private final Deque<String> recentNormalized = new ArrayDeque<>();

        State(long joinedNanos) {
            this.joinedNanos = joinedNanos;
        }

        public long joinedNanos() {
            return joinedNanos;
        }

        public boolean moved() {
            return moved;
        }

        /** The rate limit bucket, created or adjusted to the given limits. */
        public TokenBucket bucket(int burst, int secondsPerMessage, long nowNanos) {
            if (bucket == null) {
                bucket = new TokenBucket(burst, secondsPerMessage, nowNanos);
            } else {
                bucket.configure(burst, secondsPerMessage);
            }
            return bucket;
        }

        /** Cached rank cooldown in seconds, or -1 if not worked out yet. */
        public int cooldownSeconds() {
            return cooldownSeconds;
        }

        public void cooldownSeconds(int seconds) {
            cooldownSeconds = seconds;
        }

        public long lastSlowmodeMessageNanos() {
            return lastSlowmodeMessageNanos;
        }

        public void lastSlowmodeMessageNanos(long nanos) {
            lastSlowmodeMessageNanos = nanos;
        }

        public long lastMentionNanos() {
            return lastMentionNanos;
        }

        public void lastMentionNanos(long nanos) {
            lastMentionNanos = nanos;
        }

        /** The player's last normalized messages, newest last. */
        public List<String> recent() {
            return List.copyOf(recentNormalized);
        }

        /** Remembers a sent message for duplicate checks, keeping at most {@code limit}. */
        public void remember(String normalized, int limit) {
            recentNormalized.addLast(normalized);
            while (recentNormalized.size() > limit) {
                recentNormalized.removeFirst();
            }
        }
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    /** Starts tracking a player who joined. */
    public void joined(UUID player) {
        states.put(player, new State(System.nanoTime()));
    }

    /** Remembers that a player moved (for new-player protection). */
    public void moved(UUID player) {
        State state = states.get(player);
        if (state != null) {
            state.moved = true;
        }
    }

    /** Stops tracking a player who left. */
    public void left(UUID player) {
        states.remove(player);
    }

    /** The player's state; created on the fly for players the tracker missed (for example in tests). */
    public State state(UUID player) {
        return states.computeIfAbsent(player, id -> {
            // Unknown players (joined before the chat started, or tests) count as settled in.
            State state = new State(System.nanoTime() - Long.MAX_VALUE / 4);
            state.moved = true;
            return state;
        });
    }

    /** Forgets cached rank cooldowns, e.g. after a rank change or a reload. */
    public void resetCooldowns(UUID player) {
        State state = states.get(player);
        if (state != null) {
            state.cooldownSeconds = -1;
        }
    }

    /** Forgets every cached rank cooldown. */
    public void resetAllCooldowns() {
        states.values().forEach(state -> state.cooldownSeconds = -1);
    }
}
