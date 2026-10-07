package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

/** {@code /spawn} - teleports the player to the lobby spawn. Permission: {@code lobby.command.spawn}. */
public final class SpawnCommand extends Command {

    public SpawnCommand(ConfigManager configManager, PermissionService permissions) {
        super("spawn");
        setCondition(CommandSupport.requireAny(configManager, permissions, Permissions.COMMAND_SPAWN));
        setDefaultExecutor((sender, context) -> {
            ConfigSnapshot snapshot = configManager.current();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(snapshot.messages().render(MessageKey.PLAYERS_ONLY));
                return;
            }
            SpawnListener.teleportToSpawn(player, snapshot.config());
            player.sendMessage(snapshot.messages().render(MessageKey.SPAWN_TELEPORTED));
        });
    }
}
