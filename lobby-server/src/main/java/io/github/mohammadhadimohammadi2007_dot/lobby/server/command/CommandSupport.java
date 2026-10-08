package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.command.builder.condition.CommandCondition;

import java.util.Arrays;

/** Small helpers shared by the commands. */
final class CommandSupport {

    private CommandSupport() {
    }

    /**
     * A condition that passes if the sender has at least one of {@code permissions}.
     * Players without permission do not see the command in tab completion and get the
     * {@code no-permission} message if they type it anyway.
     */
    static CommandCondition requireAny(LobbyText text, PermissionService permissionService, String... permissions) {
        return (sender, commandString) -> {
            boolean allowed = Arrays.stream(permissions).anyMatch(node -> permissionService.hasPermission(sender, node));
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        };
    }
}
