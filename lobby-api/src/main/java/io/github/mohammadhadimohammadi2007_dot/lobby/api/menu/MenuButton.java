package io.github.mohammadhadimohammadi2007_dot.lobby.api.menu;

import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One item of a {@link MenuView}.
 *
 * @param slots    the slots it fills, 0 at the top left
 * @param material the item
 * @param name     MiniMessage, placeholders allowed
 * @param lore     MiniMessage lines, placeholders allowed
 * @param glow     the enchantment shine
 * @param actions  actions as in menus.yml, e.g. {@code connect_group: bedwars}; read when the menu is built
 * @param onClick  also called on a click, on a background thread, or {@code null}
 */
public record MenuButton(List<Integer> slots, Material material, String name, List<String> lore, boolean glow,
                         List<String> actions, @Nullable Consumer<Player> onClick) {

    public MenuButton {
        slots = List.copyOf(slots);
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        actions = List.copyOf(actions);
    }

    /** A button in one slot that calls {@code onClick}. */
    public static MenuButton of(int slot, Material material, String name, List<String> lore, Consumer<Player> onClick) {
        return new MenuButton(List.of(slot), material, name, lore, false, List.of(), onClick);
    }

    /** A button in one slot that runs actions. */
    public static MenuButton withActions(int slot, Material material, String name, List<String> lore, List<String> actions) {
        return new MenuButton(List.of(slot), material, name, lore, false, actions, null);
    }
}
