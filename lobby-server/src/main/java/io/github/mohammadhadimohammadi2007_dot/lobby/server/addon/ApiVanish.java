package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Vanish;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish.VanishService;
import net.minestom.server.entity.Player;

/** Vanish for addons. */
final class ApiVanish implements Vanish {

    private final VanishService vanish;

    ApiVanish(VanishService vanish) {
        this.vanish = vanish;
    }

    @Override
    public boolean vanished(Player player) {
        return vanish.isVanished(player);
    }

    @Override
    public boolean seesVanished(Player viewer) {
        return vanish.canSeeVanished(viewer);
    }

    @Override
    public boolean vanish(Player player, boolean vanished) {
        return vanish.set(player, vanished) == VanishService.Change.DONE;
    }
}
