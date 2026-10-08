package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.PersianReshaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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

    /**
     * Fixes Persian text in every text part of a component. Each part is fixed on its own, which is right for
     * normal messages; a single sentence split across differently colored parts may come out in a different
     * order than one plain sentence would.
     */
    public static Component reshapePersian(Component component) {
        Component result = component;
        if (result instanceof TextComponent text && PersianReshaper.needsReshaping(text.content())) {
            result = text.content(PersianReshaper.reshape(text.content()));
        }
        List<Component> children = result.children();
        if (children.isEmpty()) {
            return result;
        }
        List<Component> mapped = new ArrayList<>(children.size());
        for (Component child : children) {
            mapped.add(reshapePersian(child));
        }
        return result.children(mapped);
    }
}
