package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/** A player clicked an item of a lobby menu. Cancelling it stops the item's actions. Tick thread. */
public final class MenuClickEvent extends CancellableLobbyEvent {

    private final String menu;
    private final int slot;

    public MenuClickEvent(Player player, String menu, int slot) {
        super(player);
        this.menu = menu;
        this.slot = slot;
    }

    /** The menu's name, as in menus.yml or {@code Menus.register}. */
    public String menu() {
        return menu;
    }

    /** The slot clicked, 0 at the top left. */
    public int slot() {
        return slot;
    }
}
