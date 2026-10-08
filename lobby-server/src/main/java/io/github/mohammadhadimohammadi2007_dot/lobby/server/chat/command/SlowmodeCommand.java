package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;

/** {@code /slowmode <seconds|off>}: every player can send one message per that many seconds. */
public final class SlowmodeCommand extends Command {

    private static final int MAX_SECONDS = 3600;

    public SlowmodeCommand(ChatServices services) {
        super("slowmode");
        setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_SLOWMODE));
        setDefaultExecutor((sender, context) -> sender.sendMessage(services.text().message(MessageKey.SLOWMODE_USAGE, sender)));
        ArgumentWord value = ArgumentType.Word("seconds");
        addSyntax((sender, context) -> {
            String input = context.get(value);
            int seconds;
            if (input.equalsIgnoreCase("off")) {
                seconds = 0;
            } else {
                try {
                    seconds = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    sender.sendMessage(services.text().message(MessageKey.SLOWMODE_USAGE, sender));
                    return;
                }
                if (seconds < 0 || seconds > MAX_SECONDS) {
                    sender.sendMessage(services.text().message(MessageKey.SLOWMODE_USAGE, sender));
                    return;
                }
            }
            services.moderation().slowmodeSeconds(seconds);
            services.tellStaff("", seconds == 0
                    ? services.text().message(MessageKey.SLOWMODE_OFF)
                    : services.text().message(MessageKey.SLOWMODE_ON, Messages.text("seconds", seconds)));
        }, value);
    }
}
