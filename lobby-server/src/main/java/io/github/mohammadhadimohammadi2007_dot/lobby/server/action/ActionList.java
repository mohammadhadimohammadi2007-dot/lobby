package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The actions of one NPC click, menu item, hotbar item or portal, run in order. A click during the
 * cooldown is ignored, so double clicks and walking around in a portal do not run everything twice.
 */
public final class ActionList {

    public static final ActionList EMPTY = new ActionList(List.of(), 0);
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionList.class);
    /** Above this many remembered players, expired entries are cleaned out. */
    private static final int CLEANUP_SIZE = 512;

    private final List<Action> actions;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastRun = new ConcurrentHashMap<>();

    public ActionList(List<Action> actions, long cooldownMillis) {
        this.actions = List.copyOf(actions);
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    public List<Action> actions() {
        return actions;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    /** The same actions with another cooldown. */
    public ActionList withCooldown(long millis) {
        return new ActionList(actions, millis);
    }

    /** The same cooldown with other actions. */
    public ActionList withActions(List<Action> newActions) {
        return new ActionList(newActions, cooldownMillis);
    }

    /**
     * Runs the actions for {@code player} in the background.
     *
     * @return false if nothing ran because the player is still on cooldown (or there is nothing to run)
     */
    public boolean run(Player player, ActionServices services, String source) {
        return run(player, services, source, () -> { });
    }

    /** Like {@link #run(Player, ActionServices, String)}, with what to do when a {@code connect} fails. */
    public boolean run(Player player, ActionServices services, String source, Runnable onConnectFailed) {
        if (actions.isEmpty() || !passCooldown(player.getUuid(), System.currentTimeMillis())) {
            return false;
        }
        ActionContext context = new ActionContext(player, services, source, onConnectFailed);
        Async.run(() -> runFrom(0, context));
        return true;
    }

    /** True (and remembered) if the player may run the list now. Package-private for tests. */
    boolean passCooldown(UUID player, long nowMillis) {
        if (cooldownMillis == 0) {
            return true;
        }
        Long previous = lastRun.get(player);
        if (previous != null && nowMillis - previous < cooldownMillis) {
            return false;
        }
        lastRun.put(player, nowMillis);
        if (lastRun.size() > CLEANUP_SIZE) {
            lastRun.values().removeIf(time -> nowMillis - time >= cooldownMillis);
        }
        return true;
    }

    /** Runs actions from {@code index} on; a {@code wait} schedules the rest. Package-private for tests. */
    void runFrom(int index, ActionContext context) {
        for (int i = index; i < actions.size(); i++) {
            if (!context.player().isOnline()) {
                return;
            }
            Action.Step step;
            try {
                step = actions.get(i).run(context);
            } catch (RuntimeException e) {
                LOGGER.error("Action '{}' of {} failed for {}", actions.get(i).describe(), context.source(),
                        context.player().getUsername(), e);
                return;
            }
            if (step.stop()) {
                return;
            }
            if (step.waitTicks() > 0) {
                int next = i + 1;
                MinecraftServer.getSchedulerManager()
                        .buildTask(() -> Async.run(() -> runFrom(next, context)))
                        .delay(TaskSchedule.tick(step.waitTicks()))
                        .schedule();
                return;
            }
        }
    }
}
