package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.RankInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.minestom.server.entity.Player;

/** Permission checks for addons, through the lobby's permission service. */
final class ApiPermissions implements Permissions {

    private final PermissionService permissions;

    ApiPermissions(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public boolean has(Player player, String permission) {
        return permissions.hasPermission(player, permission);
    }

    @Override
    public RankInfo rank(Player player) {
        PlayerMeta meta = permissions.meta(player);
        return new RankInfo(meta.prefix(), meta.suffix(), meta.primaryGroup(), meta.meta(), meta.weight());
    }
}
