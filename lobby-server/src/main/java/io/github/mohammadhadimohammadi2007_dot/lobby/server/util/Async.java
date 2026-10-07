package io.github.mohammadhadimohammadi2007_dot.lobby.server.util;

import net.minestom.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Runs slow work (database, HTTP, files) off the tick thread and hands results back safely.
 *
 * <p>Rule of thumb: never block inside an event listener or command. Do the slow part with
 * {@link #supply}, then touch players or the world inside {@link #onTickThread}.
 */
public final class Async {

    private static final Logger LOGGER = LoggerFactory.getLogger(Async.class);
    private static final ExecutorService VIRTUAL_THREADS = Executors.newVirtualThreadPerTaskExecutor();

    private Async() {
    }

    /** Runs {@code task} on a new virtual thread. Exceptions are logged. */
    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, VIRTUAL_THREADS).whenComplete((ignored, error) -> {
            if (error != null) {
                LOGGER.error("Background task failed", error);
            }
        });
    }

    /** Computes a value on a new virtual thread. */
    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, VIRTUAL_THREADS);
    }

    /** Runs {@code task} at the start of the next server tick, where touching players and worlds is safe. */
    public static void onTickThread(Runnable task) {
        MinecraftServer.getSchedulerManager().scheduleNextTick(task);
    }

    /** Stops accepting new tasks. Called once during shutdown. */
    public static void shutdown() {
        VIRTUAL_THREADS.shutdown();
    }
}
