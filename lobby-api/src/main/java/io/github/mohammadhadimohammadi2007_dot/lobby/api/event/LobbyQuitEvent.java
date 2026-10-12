package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player left this lobby server (to another server or the game). Tick thread. */
public final class LobbyQuitEvent extends LobbyPlayerEvent {

    public LobbyQuitEvent(Player player) {
        super(player);
    }
}
