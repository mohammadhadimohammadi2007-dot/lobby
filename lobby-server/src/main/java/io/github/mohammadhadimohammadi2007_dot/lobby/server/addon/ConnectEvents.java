package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.ServerConnectResultEvent;
import net.minestom.server.entity.Player;

/** Tells addons how sending a player to another server went. */
public final class ConnectEvents {

    private ConnectEvents() {
    }

    public static void fire(Player player, String target, boolean group, ServerConnectResultEvent.Outcome outcome) {
        if (LobbyEvents.active()) {
            LobbyEvents.fire(new ServerConnectResultEvent(player, target, group, outcome));
        }
    }
}
