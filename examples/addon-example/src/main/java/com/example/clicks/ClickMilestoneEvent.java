package com.example.clicks;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyPlayerEvent;
import net.minestom.server.entity.Player;

/**
 * An event of this addon's own: a player reached a milestone of NPC clicks. Fired with
 * {@code context.events().fire(...)} on the tick thread; other addons that depend on this one can listen to it like
 * to the lobby's events.
 */
public final class ClickMilestoneEvent extends LobbyPlayerEvent {

    private final int clicks;

    public ClickMilestoneEvent(Player player, int clicks) {
        super(player);
        this.clicks = clicks;
    }

    /** The player's clicks so far. */
    public int clicks() {
        return clicks;
    }
}
