package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders display texts once per group of viewers who would see the same thing, like holograms do: a
 * line with only {@code %server_online%} is rendered once per refresh for everybody, a line with
 * {@code %player_name%} once per player. Viewers are grouped by client tier (1.8-1.12 get the legacy text
 * with 16 colours) and by whether their Persian fix is on.
 *
 * <p>Only used on the display thread. {@link #clear()} at the start of each refresh, so placeholders are
 * read again.
 */
final class TextCache {

    /**
     * What two viewers must share to share a render.
     *
     * @param about true for a text about {@link #viewer} rather than read by them
     */
    private record Key(String template, boolean legacy, boolean reshaped, @Nullable UUID viewer, boolean about) {
    }

    /** What is known about a template; it never changes while the config stays the same. */
    private record Shape(boolean perPlayer, boolean reshapeMatters) {
    }

    private final LobbyText text;
    private final ClientTiers tiers;
    private final Map<Key, Component> rendered = new HashMap<>();
    private final Map<String, Shape> shapes = new HashMap<>();
    private int renders;

    TextCache(LobbyText text, ClientTiers tiers) {
        this.text = text;
        this.tiers = tiers;
    }

    /** Forgets this refresh's texts. */
    void clear() {
        rendered.clear();
    }

    /** Forgets what is known about templates, after a reload (placeholders may have changed). */
    void forgetTemplates() {
        shapes.clear();
        rendered.clear();
    }

    /** How many times a text was really rendered, for tests and measurements. */
    int renders() {
        return renders;
    }

    /** {@code text} as {@code viewer} should see it: the legacy version on 1.8-1.12. */
    Component render(TieredText text, Player viewer) {
        boolean legacy = tiers.legacyText(viewer);
        return render(text.template(legacy), viewer, legacy);
    }

    private Component render(String template, Player viewer, boolean legacy) {
        if (template.isEmpty()) {
            return Component.empty();
        }
        Shape shape = shapes.computeIfAbsent(template, ignored -> new Shape(
                this.text.placeholders().scopeOf(template).perPlayer(), HologramText.needsReshaping(template)));
        boolean reshaped = shape.reshapeMatters() && this.text.transformsFor(viewer);
        Key key = new Key(template, legacy, reshaped, shape.perPlayer() ? viewer.getUuid() : null, false);
        Component cached = rendered.get(key);
        if (cached != null) {
            return cached;
        }
        renders++;
        Component component = this.text.render(template, viewer);
        if (legacy) {
            component = ComponentTransforms.downsampleColors(component);
        }
        rendered.put(key, component);
        return component;
    }

    /**
     * A text about {@code target} (their name or rank), the same for every viewer: rendered without
     * anybody's Persian fix, which {@link LobbyText#forViewer} adds per viewer group.
     */
    Component about(String template, Player target, boolean legacy) {
        if (template.isEmpty()) {
            return Component.empty();
        }
        Key key = new Key(template, legacy, false, target.getUuid(), true);
        Component cached = rendered.get(key);
        if (cached != null) {
            return cached;
        }
        renders++;
        Component component = this.text.placeholders().render(template, target);
        if (legacy) {
            component = ComponentTransforms.downsampleColors(component);
        }
        rendered.put(key, component);
        return component;
    }
}
