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
 * player at all, so a hidden player costs their viewers nothing. Every player also gets the same check as
 * their viewer rule (what they see), and Minestom only shows a player when both agree. That way a viewer
 * whose choice changes (when it is read on join, or on a switch) is worked out alone: one check per player
 * near them, not one per pair of players online.
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
        // A new rank can make a player staff, which changes who sees them in "staff only".
        permissions.onMetaChange(id -> MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (target != null) {
                target.updateViewableRule();
            }
        }));
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
     * Gives {@code player} the rules for who sees them and whom they see. Call before they spawn (during
     * configuration): a player without an instance has nobody to check, and the spawn then applies the
     * rules once, instead of showing everyone and checking all of them again right after.
     */
    public void applyRules(Player player) {
        player.updateViewableRule(viewer -> canSee(viewer, player));
        player.updateViewerRule(entity -> !(entity instanceof Player other) || canSee(player, other));
    }

    /**
     * Works out again what {@code target} sees once their saved choice has been read. Call once, on the
     * first spawn.
     *
     * @param onLoaded run on the tick thread after the saved choice was applied (to update the switch item)
     */
    public void watch(Player target, Runnable onLoaded) {
        settings.whenLoaded(target.getUuid()).thenRun(() -> MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            if (target.isOnline()) {
                refresh(target);
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
        refresh(player);
        player.sendMessage(text.message(MessageKey.VISIBILITY_CHANGED, player,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mode",
                        text.message(modeName(next), player))));
        return next;
    }

    /**
     * Works out again which players {@code viewer} sees, after their choice changed. Only the players near
     * them are checked; everyone else's view stays as it is.
     */
    public void refresh(Player viewer) {
        viewer.updateViewerRule();
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
