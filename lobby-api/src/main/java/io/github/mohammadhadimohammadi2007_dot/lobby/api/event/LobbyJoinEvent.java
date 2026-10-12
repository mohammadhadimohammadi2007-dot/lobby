package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * A player joined this lobby server and is in a lobby instance, after the lobby's own join handling (spawn,
 * hotbar, scoreboard). Not fired when a player only switches between lobby instances. Tick thread.
 */
public final class LobbyJoinEvent extends LobbyPlayerEvent {

    public LobbyJoinEvent(Player player) {
        super(player);
    }
}
