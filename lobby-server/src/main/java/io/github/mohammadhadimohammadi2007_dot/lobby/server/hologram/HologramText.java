package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.ComponentTransforms;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.EntityPart;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the text of a hologram for one group of viewers. Shared by holograms and the name tags of
 * NPCs, so both follow exactly the same rules: the same placeholders, the same classification, the
 * same renderers for new and old clients.
 */
public final class HologramText {

    /**
     * What decides whether two viewers can share one rendered text: their client generation, whether
     * their texts are reshaped for right-to-left reading, and, only for text that really differs per
     * player, who they are.
     */
    public record Variant(boolean legacy, boolean reshaped, @Nullable UUID viewer) {
    }

    private HologramText() {
    }

    /**
     * The variant a viewer belongs to for this text.
     *
     * @param legacy    true for a client older than 1.19.4
     * @param perViewer true if something else already makes this differ per viewer (a mirrored skin)
     */
    public static Variant variant(HologramData data, LobbyText text, Player viewer, boolean legacy,
                                  boolean perViewer) {
        // Persian text has two versions, not one per player: the fix is a setting with two values.
        boolean reshaped = data.reshapeMatters() && text.transformsFor(viewer);
        // Only text that really differs per player gets its own render; a player count does not.
        boolean ownRender = perViewer || data.textScope().perPlayer();
        return new Variant(legacy, reshaped, ownRender ? viewer.getUuid() : null);
    }

    /**
     * Works out how much a hologram's text can differ between players, which decides whether it is
     * rendered once for everybody or once per viewer. Call it whenever the lines change.
     *
     * <p>Persian and Arabic text is not per-player: the right-to-left fix is a setting with two values,
     * so the text has one version with it and one without, each rendered once.
     */
    public static void classify(HologramData data, PlaceholderService placeholders) {
        PlaceholderScope scope = PlaceholderScope.STATIC;
        boolean reshapeMatters = false;
        for (List<String> frame : data.frames()) {
            scope = scope.and(placeholders.scopeOf(frame));
            reshapeMatters = reshapeMatters || frame.stream().anyMatch(HologramText::needsReshaping);
        }
        data.textScope(scope);
        data.reshapeMatters(reshapeMatters);
    }

    /** True if the line contains Arabic-script letters, which the Persian fix may reorder. */
    private static boolean needsReshaping(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.UnicodeBlock.of(line.charAt(i)) == Character.UnicodeBlock.ARABIC) {
                return true;
            }
        }
        return false;
    }

    /** The entities of the hologram for one variant, with its text rendered for {@code viewer}. */
    public static List<EntityPart> parts(HologramData data, LobbyText text, Player viewer, boolean legacy) {
        List<Component> lines = data.type() == HologramType.TEXT ? lines(data, text, viewer, legacy) : List.of();
        return legacy ? HologramParts.legacy(data, lines) : HologramParts.modern(data, lines);
    }

    /** The current frame's lines, with placeholders filled in for {@code viewer}. */
    public static List<Component> lines(HologramData data, LobbyText text, Player viewer, boolean legacy) {
        List<String> frame = currentFrame(data);
        List<Component> rendered = new ArrayList<>(frame.size());
        for (String line : frame) {
            Component component = text.render(line, viewer);
            // Old clients cannot show the RGB colours, so they are mapped to the 16 they know.
            rendered.add(legacy ? ComponentTransforms.downsampleColors(component) : component);
        }
        return rendered;
    }

    /**
     * The frame to show now. It comes from the clock, not from a counter, so every group of viewers is
     * on the same frame even though they are rendered separately.
     */
    static List<String> currentFrame(HologramData data) {
        List<List<String>> frames = data.frames();
        if (frames.size() == 1) {
            return frames.getFirst();
        }
        long frameMillis = (long) Math.max(1, data.effectiveUpdateIntervalTicks()) * MinecraftServer.TICK_MS;
        int index = (int) Math.floorMod(System.currentTimeMillis() / frameMillis, frames.size());
        return frames.get(index);
    }
}
