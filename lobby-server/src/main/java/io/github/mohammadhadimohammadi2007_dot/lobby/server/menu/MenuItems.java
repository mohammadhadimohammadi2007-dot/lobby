package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.List;

/** Turns a {@link MenuItem} into the item one player sees, with placeholders filled in. */
public final class MenuItems {

    private MenuItems() {
    }

    /** The item for this viewer. Old clients get the 16 colors, since they cannot show the others. */
    public static ItemStack build(MenuItem item, Player viewer, LobbyText text, boolean legacyClient) {
        ItemStack.Builder builder = ItemStack.builder(item.material()).amount(item.amount());
        if (!item.name().isBlank()) {
            builder.customName(clean(text.render(item.name(), viewer), legacyClient));
        }
        if (!item.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(item.lore().size());
            for (String line : item.lore()) {
                lines.add(clean(text.render(line, viewer), legacyClient));
            }
            builder.lore(lines);
        }
        if (item.glow()) {
            builder.glowing();
        }
        return builder.build();
    }

    /** A filler item with no name, for the empty slots of a menu. */
    static ItemStack filler(Material material) {
        return ItemStack.builder(material).customName(Component.empty()).build();
    }

    /**
     * Minecraft shows item names and lore in italics by default, which looks wrong for menu text, so
     * italics are turned off unless the config asked for them.
     */
    private static Component clean(Component component, boolean legacyClient) {
        Component result = component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET
                ? component.decoration(TextDecoration.ITALIC, false)
                : component;
        if (legacyClient) {
            result = ComponentTransforms.downsampleColors(result);
            if (result.color() == null) {
                result = result.color(NamedTextColor.WHITE);
            }
        }
        return result;
    }
}
