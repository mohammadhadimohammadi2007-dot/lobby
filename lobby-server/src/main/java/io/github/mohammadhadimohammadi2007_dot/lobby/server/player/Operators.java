package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import net.minestom.server.entity.Player;

/** Who is an operator right now (and so has every permission). */
@FunctionalInterface
public interface Operators {

    /** Nobody. */
    Operators NONE = player -> false;

    /** True if {@code player} is an operator who may use their rights from this connection. Fast; any thread. */
    boolean isOp(Player player);
}
