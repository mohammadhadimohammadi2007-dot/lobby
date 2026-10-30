package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

/** {@code /cosmetics}: opens the cosmetics menu. Permission {@code lobby.command.cosmetics} (everyone by default). */
public final class CosmeticsCommand extends Command {

    public CosmeticsCommand(CosmeticsMenu menu, LobbyText text, PermissionService permissions) {
        super("cosmetics");
        setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, Permissions.COMMAND_COSMETICS);
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                menu.open(player);
            } else {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
            }
        });
    }
}
