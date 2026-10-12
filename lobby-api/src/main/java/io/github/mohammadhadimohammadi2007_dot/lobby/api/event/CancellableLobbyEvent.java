package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;

/** Base of the lobby's events that a listener can cancel. */
public abstract class CancellableLobbyEvent extends LobbyPlayerEvent implements CancellableEvent {

    private volatile boolean cancelled;

    protected CancellableLobbyEvent(Player player) {
        super(player);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
