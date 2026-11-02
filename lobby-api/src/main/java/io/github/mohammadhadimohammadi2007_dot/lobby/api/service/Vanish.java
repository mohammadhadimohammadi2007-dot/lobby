package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.minestom.server.entity.Player;

/**
 * Vanished staff: hidden from players without {@code lobby.vanish.see}, and left out of the lobby's player counts.
 * Changes are followed with {@code VanishChangeEvent}. Reading works on any thread; {@link #vanish} on the tick
 * thread.
 */
@NonExtendable
public interface Vanish {

    boolean vanished(Player player);

    /** True if {@code viewer} may see vanished players. */
    boolean seesVanished(Player viewer);

    /**
     * Vanishes the player or makes them visible again, as {@code /vanish} does (saved, and they are told).
     *
     * @return false if nothing changed: it already was so, the player is in the PvP area, or a listener cancelled it
     */
    boolean vanish(Player player, boolean vanished);
}
