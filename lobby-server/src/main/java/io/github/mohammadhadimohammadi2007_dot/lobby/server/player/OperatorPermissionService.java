package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import net.minestom.server.entity.Player;

import java.util.Locale;

/**
 * Simple permissions used when LuckPerms is disabled: players listed under {@code operators:} in
 * config.yml have every permission; everybody else only has {@link Permissions#EVERYONE}.
 * Changes to the list apply after {@code /lobby reload}.
 */
public final class OperatorPermissionService implements PermissionService {

    private final ConfigManager configManager;

    public OperatorPermissionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String name() {
        return "operators list";
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        return Permissions.EVERYONE.contains(permission) || isOperator(player);
    }

    @Override
    public PlayerMeta meta(Player player) {
        return PlayerMeta.EMPTY;
    }

    private boolean isOperator(Player player) {
        return configManager.current().config().operators().contains(player.getUsername().toLowerCase(Locale.ROOT));
    }
}
