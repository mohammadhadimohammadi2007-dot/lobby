package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ProxyCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.CommandWhitelist;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * {@code player_command: <command>} runs a command as the player (without "/"), with their permissions. A
 * lobby command runs on the lobby; any other command (such as {@code server bedwars} or a queue plugin's
 * {@code queue bedwars}) is sent to the proxy and runs there as the player, with their permissions on the
 * proxy. {@code proxy_command} always goes to the proxy.
 */
public record PlayerCommandAction(String command) implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerCommandAction.class);

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        String filled = ProxyCommands.clean(context.services().text().placeholders().plainText(command, player));
        if (filled == null) {
            LOGGER.warn("'{}' of {} is empty or longer than {} characters once its placeholders are filled in",
                    describe(), context.source(), ProxyCommands.MAX_LENGTH);
            return Step.CONTINUE;
        }
        String[] parts = filled.split(" ", 2);
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!MinecraftServer.getCommandManager().commandExists(root)) {
            context.services().proxyCommands().run(player, filled, context.source());
            return Step.CONTINUE;
        }
        // Minestom's command names are lower case; an admin may have written /Spawn.
        String local = parts.length > 1 ? root + " " + parts[1] : root;
        // commands.yml only limits what players type; an action may run any command the player has permission for.
        Async.onTickThread(() -> CommandWhitelist.runFromAction(
                () -> MinecraftServer.getCommandManager().execute(player, local)));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "player_command: " + command;
    }
}
