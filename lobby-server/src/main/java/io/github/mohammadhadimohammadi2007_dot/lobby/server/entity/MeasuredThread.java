package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One daemon thread that runs tasks in order and counts how busy it is ({@link DisplayLoad}), for work
 * that must stay off the tick thread and needs its own number in {@code /lobby info}.
 */
public final class MeasuredThread {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeasuredThread.class);

    private final String name;
    private final DisplayLoad load = new DisplayLoad();
    private final ExecutorService executor;

    public MeasuredThread(String name) {
        this.name = name;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Runs {@code task} on this thread after everything queued before it, and counts its time. */
    public void execute(Runnable task) {
        executor.execute(() -> {
            long start = System.nanoTime();
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.error("Task on {} failed", name, e);
            } finally {
                load.record(start, System.nanoTime() - start);
            }
        });
    }

    /** How busy this thread was over the last minute. */
    public DisplayLoad.Snapshot load() {
        return load.snapshot();
    }

    /** Stops the thread; queued tasks are dropped. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
