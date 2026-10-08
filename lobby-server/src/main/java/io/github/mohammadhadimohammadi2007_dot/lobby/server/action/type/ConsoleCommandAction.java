package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;

/** {@code console_command: <command>} runs a lobby command as the console, e.g. {@code "lobby info"}. */
public record ConsoleCommandAction(String command) implements Action {

    @Override
    public Step run(ActionContext context) {
        String filled = context.services().text().placeholders().plainText(command, context.player());
        Async.onTickThread(() -> {
            CommandManager commands = MinecraftServer.getCommandManager();
            commands.execute(commands.getConsoleSender(), filled);
        });
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "console_command: " + command;
    }
}
