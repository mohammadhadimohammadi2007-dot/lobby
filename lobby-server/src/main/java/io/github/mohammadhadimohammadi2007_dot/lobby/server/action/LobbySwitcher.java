package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

/** Moves players between the lobby instances of this server. */
public interface LobbySwitcher {

    /** Moves {@code player} to lobby {@code number} (1-based). */
    void switchTo(Player player, int number);
}
