package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The boss bar and the action bar, each a rotation of messages, and the title shown after joining.
 *
 * <p>Each player has their own boss bar, because its text has their placeholders. Adventure only sends
 * an update when a value really changed, so an unchanged bar costs nothing. Only used on the display
 * thread.
 */
final class BarsService {

    /** The client shows an action bar for about three seconds, so it is sent again before that. */
    private static final int ACTION_BAR_RESEND_TICKS = 40;
    /** How often a boss bar's placeholders are filled in again. */
    private static final int BOSS_BAR_UPDATE_TICKS = 20;

    private final PermissionService permissions;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    BarsService(PermissionService permissions) {
        this.permissions = permissions;
    }

    void refresh(BarsConfig config, Collection<Player> online, TextCache text, long previousTicks, long ticks) {
        BarsConfig.Rotation boss = config.bossBar();
        BarsConfig.Rotation action = config.actionBar();
        for (Player player : online) {
            UUID id = player.getUuid();
            // Only when this player's turn comes round: the next message, or fresh placeholders.
            boolean bossDue = !bossBars.containsKey(id) || !boss.enabled()
                    || Stagger.due(previousTicks, ticks, BOSS_BAR_UPDATE_TICKS, id)
                    || Stagger.due(previousTicks, ticks, boss.intervalTicks(), id);
            if (bossDue) {
                bossBar(player, boss.enabled() ? current(boss, player, ticks) : null, text);
            }
            boolean actionDue = action.enabled() && (Stagger.due(previousTicks, ticks, ACTION_BAR_RESEND_TICKS, id)
                    || Stagger.due(previousTicks, ticks, action.intervalTicks(), id));
            if (actionDue) {
                BarsConfig.Message message = current(action, player, ticks);
                if (message != null) {
                    player.sendActionBar(text.render(message.text(), player));
                }
            }
        }
    }

    /** The message of a rotation this player gets now, or {@code null} if none is for them. */
    private @Nullable BarsConfig.Message current(BarsConfig.Rotation rotation, Player player, long ticks) {
        List<BarsConfig.Message> allowed = rotation.messages().stream()
                .filter(message -> message.permission().isEmpty()
                        || permissions.hasPermission(player, message.permission()))
                .toList();
        if (allowed.isEmpty()) {
            return null;
        }
        return allowed.get(Stagger.index(ticks, rotation.intervalTicks(), allowed.size(), player.getUuid()));
    }

    private void bossBar(Player player, @Nullable BarsConfig.Message message, TextCache text) {
        BossBar bar = bossBars.get(player.getUuid());
        if (message == null) {
            if (bar != null) {
                player.hideBossBar(bar);
                bossBars.remove(player.getUuid());
            }
            return;
        }
        Component name = text.render(message.text(), player);
        if (bar == null) {
            bar = BossBar.bossBar(name, message.progress(), message.color(), message.overlay());
            bossBars.put(player.getUuid(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(name);
        bar.color(message.color());
        bar.overlay(message.overlay());
        bar.progress(message.progress());
    }

    /** Shows the join title after its delay. Call on the player's first spawn. */
    void scheduleJoinTitle(BarsConfig.JoinTitle title, Player player, TextCache text,
                           java.util.function.Consumer<Runnable> onDisplayThread) {
        if (!title.enabled() || (title.title().isEmpty() && title.subtitle().isEmpty())) {
            return;
        }
        MinecraftServer.getSchedulerManager().buildTask(() -> onDisplayThread.accept(() -> {
            if (player.isOnline()) {
                player.showTitle(Title.title(text.render(title.title(), player), text.render(title.subtitle(), player),
                        Title.Times.times(ticks(title.fadeIn()), ticks(title.stay()), ticks(title.fadeOut()))));
            }
        })).delay(net.minestom.server.timer.TaskSchedule.tick(Math.max(1, title.delay()))).schedule();
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis((long) ticks * MinecraftServer.TICK_MS);
    }

    /** Hides every boss bar (the boss bar was turned off). */
    void hideAll(Collection<Player> online) {
        for (Player player : online) {
            BossBar bar = bossBars.remove(player.getUuid());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
    }

    void forget(UUID player) {
        bossBars.remove(player);
    }
}
