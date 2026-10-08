package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter.ChatFilter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ChatRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam.ViolationTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.ChatLog;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything the chat stages and commands share. Built once at startup.
 *
 * @param filter the current chat filter; replaced on {@code /lobby reload}
 */
public record ChatServices(
        ConfigManager config,
        LobbyText text,
        PermissionService permissions,
        MuteService mutes,
        ChatModeration moderation,
        ChatSettingsService settings,
        ChatPlayerTracker players,
        AtomicReference<ChatFilter> filter,
        ViolationTracker violations,
        BridgeService bridge,
        ChatRenderer renderer,
        ChatHistory history,
        ChatLog log
) {

    /** The chat config currently in use. */
    public ChatConfig chat() {
        return config.current().chat();
    }

    /** True if {@code player} has {@code permission}. */
    public boolean has(Player player, String permission) {
        return permission.isEmpty() || permissions.hasPermission(player, permission);
    }

    /** Sends {@code message} to every online player with {@code permission} ("" = everyone). */
    public void tellStaff(String permission, Component message) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (has(player, permission)) {
                player.sendMessage(message);
            }
        }
    }
}
