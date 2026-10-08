package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam;

/**
 * A rate limit: up to {@code capacity} messages at once, then one more every {@code secondsPerToken}.
 * Not thread-safe; the chat thread owns all buckets.
 */
public final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private double capacity;
    private double secondsPerToken;
    private double tokens;
    private long lastRefillNanos;

    /**
     * @param capacity        messages that can be sent in a burst (at least 1)
     * @param secondsPerToken seconds until one more message is allowed
     * @param nowNanos        current {@link System#nanoTime()}
     */
    public TokenBucket(double capacity, double secondsPerToken, long nowNanos) {
        this.capacity = Math.max(1, capacity);
        this.secondsPerToken = Math.max(0, secondsPerToken);
        this.tokens = this.capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** Changes the limits (for example after a rank change) without resetting the tokens. */
    public void configure(double newCapacity, double newSecondsPerToken) {
        capacity = Math.max(1, newCapacity);
        secondsPerToken = Math.max(0, newSecondsPerToken);
        tokens = Math.min(tokens, capacity);
    }

    /** Takes one token if available. */
    public boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    /** Seconds until the next token, 0 if one is available now. */
    public double secondsUntilNext(long nowNanos) {
        refill(nowNanos);
        return tokens >= 1 ? 0 : (1 - tokens) * secondsPerToken;
    }

    private void refill(long nowNanos) {
        if (secondsPerToken == 0) {
            tokens = capacity;
        } else {
            double elapsed = (nowNanos - lastRefillNanos) / NANOS_PER_SECOND;
            tokens = Math.min(capacity, tokens + elapsed / secondsPerToken);
        }
        lastRefillNanos = nowNanos;
    }
}
