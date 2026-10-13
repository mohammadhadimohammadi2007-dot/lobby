package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player left the PvP area and got the lobby's hotbar back. Tick thread. */
public final class PvpAreaLeaveEvent extends LobbyPlayerEvent {

    public PvpAreaLeaveEvent(Player player) {
        super(player);
    }
}
