package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler.AddonScheduler;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler.AddonTask;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** An addon's tasks, on Minestom's scheduler and the lobby's virtual threads; all cancelled on disable. */
final class ApiScheduler implements AddonScheduler {

    private static final long MILLIS_PER_TICK = 50;

    private final AddonContextImpl context;
    private final Set<TaskHandle> tasks = ConcurrentHashMap.newKeySet();

    ApiScheduler(AddonContextImpl context) {
        this.context = context;
    }

    @Override
    public CompletableFuture<Void> async(Runnable task) {
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return Async.supply(() -> {
            if (!context.running()) {
                throw new IllegalStateException("addon '" + context.description().id() + "' is disabled");
            }
            return task.get();
        }).whenComplete((ignored, error) -> {
            if (error != null && context.running()) {
                context.logger().error("A background task failed", error);
            }
        });
    }

    @Override
    public void nextTick(Runnable task) {
        Async.onTickThread(() -> runSafely(task));
    }

    @Override
    public AddonTask later(Duration delay, Runnable task) {
        TaskHandle handle = new TaskHandle();
        handle.task = MinecraftServer.getSchedulerManager().buildTask(() -> {
                    tasks.remove(handle);
                    runSafely(task);
                })
                .delay(TaskSchedule.tick(ticks(delay)))
                .schedule();
        return track(handle);
    }

    @Override
    public AddonTask repeat(Duration period, Runnable task) {
        int ticks = ticks(period);
        TaskHandle handle = new TaskHandle();
        handle.task = MinecraftServer.getSchedulerManager().buildTask(() -> runSafely(task))
                .delay(TaskSchedule.tick(ticks))
                .repeat(TaskSchedule.tick(ticks))
                .schedule();
        return track(handle);
    }

    @Override
    public AddonTask repeatAsync(Duration period, Runnable task) {
        int ticks = ticks(period);
        AtomicBoolean busy = new AtomicBoolean();
        TaskHandle handle = new TaskHandle();
        handle.task = MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (busy.compareAndSet(false, true)) {
                        Async.run(() -> {
                            try {
                                runSafely(task);
                            } finally {
                                busy.set(false);
                            }
                        });
                    }
                })
                .delay(TaskSchedule.tick(ticks))
                .repeat(TaskSchedule.tick(ticks))
                .schedule();
        return track(handle);
    }

    /** Cancels every task still waiting or repeating. */
    void cleanup() {
        for (TaskHandle handle : tasks) {
            handle.cancel();
        }
        tasks.clear();
    }

    /** How many tasks are waiting or repeating, for tests. */
    int pending() {
        return tasks.size();
    }

    private TaskHandle track(TaskHandle handle) {
        tasks.add(handle);
        if (!context.running()) {
            handle.cancel();
        }
        return handle;
    }

    private void runSafely(Runnable task) {
        if (!context.running()) {
            return;
        }
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            context.logger().error("A task failed", e);
        }
    }

    private static int ticks(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Durations must not be negative: " + duration);
        }
        long ticks = (duration.toMillis() + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ticks));
    }

    private final class TaskHandle implements AddonTask {

        private volatile Task task;
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
            Task current = task;
            if (current != null) {
                current.cancel();
            }
            tasks.remove(this);
        }

        @Override
        public boolean cancelled() {
            return cancelled || (task != null && !task.isAlive());
        }
    }
}
