package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

/** Opens and closes the menus from menus.yml. */
public interface MenuHandler {

    /** Opens the menu named {@code menu}; unknown names are ignored (and were reported at load). */
    void open(Player player, String menu);

    /** Closes whatever inventory the player has open. */
    void close(Player player);
}
