package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player walked into the PvP area and got the kit. Tick thread. */
public final class PvpAreaEnterEvent extends LobbyPlayerEvent {

    public PvpAreaEnterEvent(Player player) {
        super(player);
    }
}
