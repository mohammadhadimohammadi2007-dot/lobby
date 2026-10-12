package io.github.mohammadhadimohammadi2007_dot.lobby.api.menu;

import java.util.List;
import java.util.Objects;

/**
 * The contents of a menu.
 *
 * @param title   MiniMessage, placeholders allowed
 * @param rows    1 to 6
 * @param buttons where several buttons share a slot, the first one wins
 */
public record MenuView(String title, int rows, List<MenuButton> buttons) {

    public MenuView {
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1 to 6, not " + rows);
        }
        buttons = List.copyOf(buttons);
    }
}
