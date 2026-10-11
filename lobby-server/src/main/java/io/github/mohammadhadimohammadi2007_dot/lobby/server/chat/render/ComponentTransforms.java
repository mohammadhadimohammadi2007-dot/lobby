package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/** Small whole-component changes used when rendering chat for different clients. */
public final class ComponentTransforms {

    private ComponentTransforms() {
    }

    /**
     * Replaces every color with the nearest of Minecraft's 16 named colors, for clients older than 1.16
     * (ViaVersion would do the same, but doing it here gives the same result on every proxy setup).
     */
    public static Component downsampleColors(Component component) {
        TextColor color = component.color();
        Component result = component;
        if (color != null && !(color instanceof NamedTextColor)) {
            result = result.color(NamedTextColor.nearestTo(color));
        }
        List<Component> children = result.children();
        if (children.isEmpty()) {
            return result;
        }
        List<Component> mapped = new ArrayList<>(children.size());
        for (Component child : children) {
            mapped.add(downsampleColors(child));
        }
        return result.children(mapped);
    }
}
