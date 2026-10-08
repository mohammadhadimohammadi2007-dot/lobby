package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Sends the {@code broadcasts:} messages from chat.yml every few minutes, rendered per player (placeholders,
 * Persian fixing) and with the shorter legacy version for old clients. Rebuilt on {@code /lobby reload}.
 */
public final class ChatBroadcaster {

    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final Supplier<ChatConfig> config;
    private final LobbyText text;
    private final BridgeService bridge;
    private @Nullable Task task;
    private int next;

    public ChatBroadcaster(Supplier<ChatConfig> config, LobbyText text, BridgeService bridge) {
        this.config = config;
        this.text = text;
        this.bridge = bridge;
    }

    /** Starts (or restarts) the timer with the current settings; does nothing if broadcasts are off. */
    public synchronized void restart() {
        stop();
        ChatConfig.Broadcasts settings = config.get().broadcasts();
        if (!settings.enabled() || settings.messages().isEmpty()) {
            return;
        }
        next = 0;
        TaskSchedule interval = TaskSchedule.tick(settings.intervalMinutes() * TICKS_PER_MINUTE);
        // Rendering touches placeholders, so it runs off the tick thread.
        task = MinecraftServer.getSchedulerManager().buildTask(() -> Async.run(this::broadcastNext))
                .delay(interval)
                .repeat(interval)
                .schedule();
    }

    /** Stops the timer. */
    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Sends the next message to every online player. */
    void broadcastNext() {
        ChatConfig.Broadcasts settings = config.get().broadcasts();
        int index = pick(settings);
        if (index < 0) {
            return;
        }
        String modern = settings.messages().get(index);
        List<String> legacy = settings.legacyMessages();
        String old = index < legacy.size() ? legacy.get(index) : modern;
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            boolean oldClient = bridge.capabilities(player).legacy();
            player.sendMessage(text.render(oldClient ? old : modern, player));
        }
    }

    /** The index of the message to send, or -1 if there are none. */
    synchronized int pick(ChatConfig.Broadcasts settings) {
        int count = settings.messages().size();
        if (count == 0) {
            return -1;
        }
        if (settings.random()) {
            return ThreadLocalRandom.current().nextInt(count);
        }
        int index = next % count;
        next = index + 1;
        return index;
    }
}
