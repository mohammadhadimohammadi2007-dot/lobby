package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.MenuHandler;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbySelectorMenu;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

/** {@code /lobbies} opens the lobby selector, the same menu as {@code open-menu: lobbies}. */
public final class LobbiesCommand extends Command {

    public LobbiesCommand(LobbyText text, PermissionService permissions, MenuHandler menus) {
        super("lobbies");
        setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_LOBBY));
        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            menus.open(player, LobbySelectorMenu.NAME);
        });
    }
}
