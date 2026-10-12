package io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Runs an addon's work at the right place: slow work off the tick thread, world changes on it. Tasks still
 * waiting or repeating are cancelled when the addon is disabled; one that is already running finishes.
 * Errors in tasks are logged.
 *
 * <p>Durations are rounded to whole ticks (50 ms) for tick-thread tasks.
 */
public interface AddonScheduler {

    /** Runs {@code task} on a virtual thread, where blocking (files, databases, web requests) is fine. */
    CompletableFuture<Void> async(Runnable task);

    /** Computes a value on a virtual thread. */
    <T> CompletableFuture<T> supplyAsync(Supplier<T> task);

    /** Runs {@code task} at the start of the next tick, where touching players and the world is safe. */
    void nextTick(Runnable task);

    /** Runs {@code task} on the tick thread after {@code delay}. */
    AddonTask later(Duration delay, Runnable task);

    /** Runs {@code task} on the tick thread every {@code period}, the first time after one period. */
    AddonTask repeat(Duration period, Runnable task);

    /** Runs {@code task} on a virtual thread every {@code period}; a run is skipped while the previous one is busy. */
    AddonTask repeatAsync(Duration period, Runnable task);
}
