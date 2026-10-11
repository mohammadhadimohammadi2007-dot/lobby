package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import net.minestom.server.entity.Player;

/**
 * Simple permissions used when LuckPerms is disabled: operators ({@code data/ops.yml}, see {@link OpList})
 * have every permission; everybody else only has {@link Permissions#EVERYONE}.
 */
public final class OperatorPermissionService implements PermissionService {

    private final Operators operators;

    public OperatorPermissionService(Operators operators) {
        this.operators = operators;
    }

    @Override
    public String name() {
        return "operators (data/ops.yml)";
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        return Permissions.EVERYONE.contains(permission) || operators.isOp(player);
    }

    @Override
    public PlayerMeta meta(Player player) {
        return PlayerMeta.EMPTY;
    }
}
