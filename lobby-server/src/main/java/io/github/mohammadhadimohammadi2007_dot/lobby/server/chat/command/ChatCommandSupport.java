package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import net.minestom.server.command.builder.condition.CommandCondition;

/** Shared helpers for the chat commands. */
final class ChatCommandSupport {

    private ChatCommandSupport() {
    }

    /** Allows the console and players with {@code permission}; tells others they lack permission. */
    static CommandCondition require(ChatServices services, String permission) {
        return (sender, commandString) -> {
            boolean allowed = services.permissions().hasPermission(sender, permission);
            if (!allowed && commandString != null) {
                sender.sendMessage(services.text().message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        };
    }
}
