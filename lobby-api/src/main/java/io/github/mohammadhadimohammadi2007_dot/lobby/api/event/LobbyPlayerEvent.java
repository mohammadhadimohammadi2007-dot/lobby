package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.PlayerEvent;

import java.util.Objects;

/** Base of the lobby's events about one player. */
public abstract class LobbyPlayerEvent implements PlayerEvent {

    private final Player player;

    protected LobbyPlayerEvent(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
