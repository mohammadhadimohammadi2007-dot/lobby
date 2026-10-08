package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared logic of the database and file chat logs: entries are queued without blocking and written by one
 * background thread every couple of seconds, or as soon as many are waiting.
 */
abstract class BatchingChatLog implements ChatLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchingChatLog.class);
    private static final long FLUSH_INTERVAL_SECONDS = 2;
    private static final int BATCH_SIZE = 200;
    /** Above this many waiting entries new ones are dropped, so a dead database cannot fill the memory. */
    private static final int MAX_QUEUED = 50_000;
    private static final long CLEANUP_INTERVAL_HOURS = 6;

    private final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lobby-chat-log");
        thread.setDaemon(true);
        return thread;
    });
    private boolean warnedFull;

    /** Starts the background writer and the periodic cleanup. Call once from the subclass factory. */
    protected final void start() {
        writer.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        writer.scheduleWithFixedDelay(this::safeCleanup, 1, CLEANUP_INTERVAL_HOURS * 60, TimeUnit.MINUTES);
    }

    @Override
    public final void log(Entry entry) {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet();
            if (!warnedFull) {
                warnedFull = true;
                LOGGER.warn("Chat log is falling behind; new entries are dropped until it catches up.");
            }
            return;
        }
        queue.add(entry);
        if (queued.get() >= BATCH_SIZE) {
            writer.execute(this::flush);
        }
    }

    private void flush() {
        List<Entry> batch = new ArrayList<>(BATCH_SIZE);
        Entry entry;
        while ((entry = queue.poll()) != null) {
            queued.decrementAndGet();
            batch.add(entry);
            if (batch.size() == BATCH_SIZE) {
                writeSafely(batch);
                batch = new ArrayList<>(BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) {
            writeSafely(batch);
        }
        warnedFull = false;
    }

    private void writeSafely(List<Entry> batch) {
        try {
            write(batch);
        } catch (Exception e) {
            LOGGER.warn("Could not write {} chat log entries: {}", batch.size(), e.getMessage());
        }
    }

    private void safeCleanup() {
        try {
            cleanup();
        } catch (Exception e) {
            LOGGER.warn("Chat log cleanup failed: {}", e.getMessage());
        }
    }

    /** Writes a batch. Runs on the log thread. */
    protected abstract void write(List<Entry> batch) throws Exception;

    /** Removes entries older than the retention time. Runs on the log thread. */
    protected abstract void cleanup() throws Exception;

    @Override
    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flush();
    }
}
