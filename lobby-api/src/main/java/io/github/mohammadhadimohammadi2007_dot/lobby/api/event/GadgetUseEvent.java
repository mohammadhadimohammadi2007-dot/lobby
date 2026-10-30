package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * A player is about to use their gadget (it was not on cooldown). Cancelling it does nothing and starts no
 * cooldown. Tick thread.
 */
public final class GadgetUseEvent extends CancellableLobbyEvent {

    private final String gadget;

    public GadgetUseEvent(Player player, String gadget) {
        super(player);
        this.gadget = gadget;
    }

    /** The gadget's id in cosmetics.yml. */
    public String gadget() {
        return gadget;
    }
}
