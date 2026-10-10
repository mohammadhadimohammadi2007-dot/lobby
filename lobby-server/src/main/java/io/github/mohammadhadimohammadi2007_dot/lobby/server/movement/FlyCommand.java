package io.github.mohammadhadimohammadi2007_dot.lobby.server.movement;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

/**
 * {@code /fly}: turns flying on or off. The permission is {@code fly.permission} in movement.yml, read
 * on every use, so a reload changes it right away.
 */
public final class FlyCommand extends Command {

    public FlyCommand(ConfigManager config, LobbyText text, PermissionService permissions, MovementService movement) {
        super("fly");
        setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, config.current().movement().flyPermission());
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text.message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            boolean on = movement.toggleFly(player);
            player.sendMessage(text.message(on ? MessageKey.FLY_ON : MessageKey.FLY_OFF, player));
        });
    }
}
