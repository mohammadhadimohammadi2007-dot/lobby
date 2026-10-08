package io.github.mohammadhadimohammadi2007_dot.lobby.server.region;

import net.minestom.server.entity.Player;

/** Told when players walk into or out of regions. Called on the tick thread, so it must be quick. */
public interface RegionListener {

    void entered(Player player, Region region);

    default void left(Player player, Region region) {
    }
}
