package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

/**
 * {@code player_command: <command>} runs a lobby command as the player (without "/"), with their
 * permissions. Commands of proxy plugins cannot be run this way; use {@code connect} for server switching.
 */
public record PlayerCommandAction(String command) implements Action {

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        String filled = context.services().text().placeholders().plainText(command, player);
        Async.onTickThread(() -> MinecraftServer.getCommandManager().execute(player, filled));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "player_command: " + command;
    }
}
