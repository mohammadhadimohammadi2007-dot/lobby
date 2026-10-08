package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One hologram as stored in {@code data/holograms.yml}.
 *
 * <p>This is the one mutable model in the lobby, on purpose: {@code /hologram edit} changes a single
 * property of a live hologram, and copying seventeen values to change one would help nobody. Every
 * field is {@code volatile} because the display thread reads them while a command thread writes them,
 * and a half-written hologram would only show for one refresh anyway.
 *
 * <p>Changing something here does not show it: the service saves the file and tells the renderer.
 */
public final class HologramData {

    /** Most lines one hologram may have; more than this is a config mistake, not a design. */
    public static final int MAX_LINES = 32;
    public static final int MAX_LINE_LENGTH = 512;
    public static final double MIN_SCALE = 0.05;
    public static final double MAX_SCALE = 20;
    public static final double MIN_VIEW_DISTANCE = 1;
    public static final double MAX_VIEW_DISTANCE = 128;
    public static final int MAX_UPDATE_INTERVAL_TICKS = 20 * 60;
    public static final double MIN_LINE_SPACING = 0.05;
    public static final double MAX_LINE_SPACING = 2;
    /** Default space between the lines of a hologram on old clients, in blocks. */
    public static final double DEFAULT_LINE_SPACING = 0.27;
    public static final double DEFAULT_VIEW_DISTANCE = 48;
    /** How often a hologram with placeholders is rebuilt when no interval is set. */
    public static final int PLACEHOLDER_UPDATE_TICKS = 20;
    /** {@code update-interval: -1}: decide from the text (placeholders or an animation need one). */
    public static final int AUTOMATIC_UPDATE_INTERVAL = -1;

    private final String name;
    private volatile HologramType type;
    private volatile Pos position;
    private volatile List<List<String>> frames;
    private volatile @Nullable Material item;
    private volatile @Nullable Block block;
    private volatile double scale = 1;
    private volatile Billboard billboard = Billboard.CENTER;
    private volatile TextAlignment alignment = TextAlignment.CENTER;
    private volatile @Nullable Integer background;
    private volatile boolean textShadow;
    private volatile boolean seeThrough;
    private volatile double viewDistance = DEFAULT_VIEW_DISTANCE;
    private volatile int updateIntervalTicks = AUTOMATIC_UPDATE_INTERVAL;
    private volatile String permission = "";
    private volatile double lineSpacing = DEFAULT_LINE_SPACING;
    private volatile List<String> actionLines = List.of();
    private volatile ActionList actions = ActionList.EMPTY;

    public HologramData(String name, HologramType type, Pos position, List<String> lines) {
        this.name = name;
        this.type = type;
        this.position = position;
        this.frames = List.of(List.copyOf(lines));
    }

    public String name() {
        return name;
    }

    public HologramType type() {
        return type;
    }

    public void type(HologramType value) {
        type = value;
    }

    public Pos position() {
        return position;
    }

    public void position(Pos value) {
        position = value;
    }

    /** The lines shown now are one frame; several frames make an animation. */
    public List<List<String>> frames() {
        return frames;
    }

    public void frames(List<List<String>> value) {
        frames = value.isEmpty() ? List.of(List.of()) : List.copyOf(value.stream().map(List::copyOf).toList());
    }

    /** The first frame: what a hologram without an animation shows, and what commands edit. */
    public List<String> lines() {
        return frames.getFirst();
    }

    public void lines(List<String> value) {
        List<List<String>> updated = new ArrayList<>(frames);
        updated.set(0, List.copyOf(value));
        frames = List.copyOf(updated);
    }

    /** True if this hologram cycles through several frames. */
    public boolean animated() {
        return frames.size() > 1;
    }

    public @Nullable Material item() {
        return item;
    }

    public void item(@Nullable Material value) {
        item = value;
    }

    public @Nullable Block block() {
        return block;
    }

    public void block(@Nullable Block value) {
        block = value;
    }

    public double scale() {
        return scale;
    }

    public void scale(double value) {
        scale = clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public Billboard billboard() {
        return billboard;
    }

    public void billboard(Billboard value) {
        billboard = value;
    }

    public TextAlignment alignment() {
        return alignment;
    }

    public void alignment(TextAlignment value) {
        alignment = value;
    }

    /** The background colour as 0xAARRGGBB, or {@code null} for Minecraft's own translucent one. */
    public @Nullable Integer background() {
        return background;
    }

    public void background(@Nullable Integer value) {
        background = value;
    }

    public boolean textShadow() {
        return textShadow;
    }

    public void textShadow(boolean value) {
        textShadow = value;
    }

    public boolean seeThrough() {
        return seeThrough;
    }

    public void seeThrough(boolean value) {
        seeThrough = value;
    }

    public double viewDistance() {
        return viewDistance;
    }

    public void viewDistance(double value) {
        viewDistance = clamp(value, MIN_VIEW_DISTANCE, MAX_VIEW_DISTANCE);
    }

    /** As written: {@link #AUTOMATIC_UPDATE_INTERVAL} means "work it out from the text". */
    public int updateIntervalTicks() {
        return updateIntervalTicks;
    }

    public void updateIntervalTicks(int value) {
        updateIntervalTicks = value < 0 ? AUTOMATIC_UPDATE_INTERVAL
                : Math.min(value, MAX_UPDATE_INTERVAL_TICKS);
    }

    /**
     * How often the content really is rebuilt: what was asked for, or once a second when the text has
     * placeholders or frames and nothing was asked for. Text without either never needs rebuilding.
     */
    public int effectiveUpdateIntervalTicks() {
        if (updateIntervalTicks != AUTOMATIC_UPDATE_INTERVAL) {
            return updateIntervalTicks;
        }
        return animated() || hasPlaceholders() ? PLACEHOLDER_UPDATE_TICKS : 0;
    }

    /** Permission needed to see it, or {@code ""} for everyone. */
    public String permission() {
        return permission;
    }

    public void permission(String value) {
        permission = value.strip();
    }

    /** Space between lines on old clients, in blocks. Modern clients use one entity for all lines. */
    public double lineSpacing() {
        return lineSpacing;
    }

    public void lineSpacing(double value) {
        lineSpacing = clamp(value, MIN_LINE_SPACING, MAX_LINE_SPACING);
    }

    /** What a click runs. */
    public ActionList actions() {
        return actions;
    }

    /** The action list as written in the file, which is what is saved again. */
    public List<String> actionLines() {
        return actionLines;
    }

    /**
     * Sets both at once: the lines as the admin wrote them, which are saved, and the parsed list, which
     * is run. They are one field in practice, so they are set together and can never disagree.
     */
    public void actions(List<String> lines, ActionList parsed) {
        actionLines = List.copyOf(lines);
        actions = parsed;
    }

    /**
     * True if any line contains a {@code %placeholder%}, which means the text can differ per player and
     * has to be rebuilt now and then.
     */
    public boolean hasPlaceholders() {
        for (List<String> frame : frames) {
            for (String line : frame) {
                if (line.indexOf('%') >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The same hologram under another name, for {@code /hologram copy}. */
    public HologramData copy(String newName) {
        HologramData copy = new HologramData(newName, type, position, lines());
        copy.frames(frames);
        copy.item = item;
        copy.block = block;
        copy.scale = scale;
        copy.billboard = billboard;
        copy.alignment = alignment;
        copy.background = background;
        copy.textShadow = textShadow;
        copy.seeThrough = seeThrough;
        copy.viewDistance = viewDistance;
        copy.updateIntervalTicks = updateIntervalTicks;
        copy.permission = permission;
        copy.lineSpacing = lineSpacing;
        copy.actionLines = actionLines;
        copy.actions = actions;
        return copy;
    }

    private static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }
}
