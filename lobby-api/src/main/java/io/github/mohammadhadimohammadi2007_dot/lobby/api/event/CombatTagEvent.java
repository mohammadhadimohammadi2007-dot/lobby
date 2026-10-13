package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * A player's combat tag started (they hit or were hit in the PvP area) or ended. While tagged they cannot leave
 * the area or switch servers. Tick thread.
 */
public final class CombatTagEvent extends LobbyPlayerEvent {

    private final boolean tagged;

    public CombatTagEvent(Player player, boolean tagged) {
        super(player);
        this.tagged = tagged;
    }

    /** True when the tag starts, false when it ends. */
    public boolean tagged() {
        return tagged;
    }
}
