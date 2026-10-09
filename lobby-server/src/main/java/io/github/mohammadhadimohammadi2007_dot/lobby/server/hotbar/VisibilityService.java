package io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who sees whom in the lobby: everybody, only staff, or nobody, chosen by each player with the hotbar
 * switch and saved with their other settings.
 *
 * <p>It only hides players. NPCs and holograms are drawn with packets for every viewer and are never
 * affected. Hiding works through Minestom's viewable rule of each player, which decides who is sent that
 * player at all, so a hidden player costs their viewers nothing.
 */
public final class VisibilityService {

    private final ConfigManager config;
    private final PermissionService permissions;
    private final ChatSettingsService settings;
    private final LobbyText text;
    private final Map<UUID, Long> lastSwitch = new ConcurrentHashMap<>();

    public VisibilityService(ConfigManager config, PermissionService permissions, ChatSettingsService settings,
                             LobbyText text) {
        this.config = config;
        this.permissions = permissions;
        this.settings = settings;
        this.text = text;
    }

    private HotbarConfig.Visibility options() {
        return config.current().hotbar().visibility();
    }

    /** What {@code viewer} chose, or the server's default if they never did. */
    public VisibilityMode mode(Player viewer) {
        VisibilityMode saved = VisibilityMode.fromName(settings.get(viewer.getUuid()).visibility());
        return saved != null ? saved : options().defaultMode();
    }

    /** True if {@code viewer} sees {@code target}. Everyone always sees themselves. */
    public boolean canSee(Player viewer, Player target) {
        if (viewer == target) {
            return true;
        }
        return switch (mode(viewer)) {
            case ALL -> true;
            case STAFF -> permissions.hasPermission(target, options().staffPermission());
            case NONE -> false;
        };
    }

    /**
     * Starts deciding who may see {@code target}. Call once, on the first spawn; when the player's saved
     * choice has been read, what they see is worked out again.
     *
     * @param onLoaded run on the tick thread after the saved choice was applied (to update the switch item)
     */
    public void watch(Player target, Runnable onLoaded) {
        target.updateViewableRule(viewer -> canSee(viewer, target));
        settings.whenLoaded(target.getUuid()).thenRun(() -> MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (target.isOnline()) {
                refreshViewer();
                onLoaded.run();
            }
        }));
    }

    /**
     * Switches {@code player} to the next mode, unless they switched too recently (then they are told how
     * long to wait).
     *
     * @return the new mode, or {@code null} if it did not change
     */
    public @Nullable VisibilityMode toggle(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = options().cooldownMillis();
        Long last = lastSwitch.get(player.getUuid());
        if (last != null && now - last < cooldown) {
            long seconds = Math.max(1, (cooldown - (now - last) + 999) / 1000);
            player.sendMessage(text.message(MessageKey.VISIBILITY_COOLDOWN, player, Messages.text("seconds", seconds)));
            return null;
        }
        lastSwitch.put(player.getUuid(), now);
        VisibilityMode next = mode(player).next();
        settings.update(player.getUuid(), current -> current.withVisibility(next.configName()));
        refreshViewer();
        player.sendMessage(text.message(MessageKey.VISIBILITY_CHANGED, player,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mode",
                        text.message(modeName(next), player))));
        return next;
    }

    /**
     * Works out again who is shown to whom. Every player's rule is asked again, which also covers a
     * viewer who changed their mode; it runs on a switch (rate-limited) and when a player's saved choice
     * arrives, never every tick.
     */
    public void refreshViewer() {
        for (Player target : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            target.updateViewableRule();
        }
    }

    /** Forgets a player who left. */
    public void forget(UUID player) {
        lastSwitch.remove(player);
    }

    private static MessageKey modeName(VisibilityMode mode) {
        return switch (mode) {
            case ALL -> MessageKey.VISIBILITY_ALL;
            case STAFF -> MessageKey.VISIBILITY_STAFF;
            case NONE -> MessageKey.VISIBILITY_NONE;
        };
    }
}
