package io.github.mohammadhadimohammadi2007_dot.lobby.api.menu;

import net.minestom.server.entity.Player;

import java.util.function.Function;

/**
 * The lobby's menus: the ones in menus.yml, and menus an addon builds in code.
 *
 * <p>Menus are read-only for players: every click is cancelled, and only the clicked button's actions run.
 * Registered menus are removed when the addon is disabled.
 */
public interface Menus {

    /** Opens a menu of menus.yml or a registered one. Nothing happens (but a warning) if there is none. Tick thread. */
    void open(Player player, String menu);

    /** Closes the player's menu, if one is open. Tick thread. */
    void close(Player player);

    /** True if a menu with that name exists in menus.yml or was registered. */
    boolean exists(String menu);

    /**
     * Adds a menu built when a player opens it, so it can depend on the player and the moment. A menu with the
     * same name in menus.yml wins, so server owners can always replace it.
     *
     * @param name    lowercase letters, digits, {@code -} and {@code _}; usable in {@code open_menu: <name>}
     * @param builder called on the tick thread when the menu opens; keep it quick
     * @throws IllegalArgumentException if the name is invalid or another addon or the lobby already uses it
     */
    void register(String name, Function<Player, MenuView> builder);
}
