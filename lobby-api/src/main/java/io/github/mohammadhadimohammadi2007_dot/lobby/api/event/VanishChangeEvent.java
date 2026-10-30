package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A staff member is about to vanish or to become visible again. Cancelling it keeps them as they are. Tick thread. */
public final class VanishChangeEvent extends CancellableLobbyEvent {

    private final boolean vanishing;

    public VanishChangeEvent(Player player, boolean vanishing) {
        super(player);
        this.vanishing = vanishing;
    }

    /** True when the player vanishes, false when they become visible again. */
    public boolean vanishing() {
        return vanishing;
    }
}
