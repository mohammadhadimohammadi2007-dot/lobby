package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import net.minestom.server.entity.Player;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * LuckPerms with operators on top, like Bukkit: an operator has every permission, unless
 * {@code ops.have-all-permissions: false}; then they only have what LuckPerms gives them.
 */
public final class OpsHaveAllPermissions implements PermissionService {

    private final PermissionService delegate;
    private final Operators operators;
    private final BooleanSupplier enabled;

    /** @param enabled {@code ops.have-all-permissions}, read on every check so a reload applies at once */
    public OpsHaveAllPermissions(PermissionService delegate, Operators operators, BooleanSupplier enabled) {
        this.delegate = delegate;
        this.operators = operators;
        this.enabled = enabled;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        return (enabled.getAsBoolean() && operators.isOp(player)) || delegate.hasPermission(player, permission);
    }

    @Override
    public OptionalInt groupWeight(String group) {
        return delegate.groupWeight(group);
    }

    @Override
    public PlayerMeta meta(Player player) {
        return delegate.meta(player);
    }

    @Override
    public void onMetaChange(Consumer<UUID> listener) {
        delegate.onMetaChange(listener);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
