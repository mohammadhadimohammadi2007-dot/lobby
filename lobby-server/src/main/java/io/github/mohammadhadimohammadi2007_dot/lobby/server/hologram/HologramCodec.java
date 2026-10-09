package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionEntries;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataNodes;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads and writes one hologram of {@code data/holograms.yml}.
 *
 * <p>Only a missing or unreadable type, position or material makes an entry broken, because those
 * cannot be guessed. Everything else falls back to its default with a warning, so a typo in a colour
 * never hides a whole hologram. Action lines are kept exactly as written, so saving a hologram again
 * never rewrites what an admin typed.
 */
public final class HologramCodec implements DataCodec<HologramData> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HologramCodec.class);
    private static final String TRANSPARENT = "transparent";
    private static final String DEFAULT = "default";
    private static final int RGB_LENGTH = 6;
    private static final int ARGB_LENGTH = 8;
    private static final int HEX = 16;
    private static final int OPAQUE = 0xFF000000;
    private static final long MAX_CLICK_COOLDOWN_MILLIS = 60_000;
    private static final String DEFAULT_BILLBOARD = "center";
    private static final String DEFAULT_ALIGNMENT = "center";
    /** Default wait between two clicks on the same hologram, so one click cannot run twice. */
    public static final long DEFAULT_CLICK_COOLDOWN_MILLIS = 500;

    @Override
    public HologramData read(ConfigurationNode node) throws DataException {
        // Names are stored in lower case, so /hologram delete Welcome finds "welcome".
        String name = String.valueOf(node.key()).toLowerCase(Locale.ROOT);
        HologramType type = HologramType.fromConfigName(node.node("type").getString("text"));
        if (type == null) {
            throw new DataException("'type' must be one of " + String.join(", ", HologramType.configNames()));
        }
        HologramData data = new HologramData(name, type, position(node.node("position")), List.of());
        readAppearance(node, data, "holograms.yml: " + name);
        readMaterials(node, data, type);
        data.permission(node.node("permission").getString(""));
        HologramVisibility visibility = HologramVisibility.fromConfigName(
                node.node("visibility").getString(HologramVisibility.ALL.configName()));
        if (visibility == null) {
            warn("holograms.yml: " + name, "visibility", node.node("visibility").getString(""),
                    HologramVisibility.configNames());
        } else {
            data.visibility(visibility);
        }
        readActions(node, data, name);
        return data;
    }

    @Override
    public void write(HologramData data, ConfigurationNode node) throws SerializationException {
        node.node("type").set(data.type().configName());
        ConfigurationNode position = node.node("position");
        position.node("x").set(data.position().x());
        position.node("y").set(data.position().y());
        position.node("z").set(data.position().z());
        if (data.position().yaw() != 0 || data.position().pitch() != 0) {
            position.node("yaw").set(data.position().yaw());
            position.node("pitch").set(data.position().pitch());
        }
        if (data.item() != null) {
            node.node("item").set(data.item().key().value());
        }
        if (data.block() != null) {
            node.node("block").set(data.block().key().value());
        }
        writeAppearance(data, node, data.type() == HologramType.TEXT);
        node.node("permission").set(data.permission());
        node.node("visibility").set(data.visibility().configName());
        if (!data.actionEntries().isEmpty()) {
            node.node("click-cooldown").set(data.actions().cooldownMillis());
            ActionEntries.write(data.actionEntries(), node.node("actions"));
        }
    }

    /**
     * Reads how a hologram looks: its lines and frames, size, colours, light, shadow, view distance and
     * update interval. Everything but where it is, what kind it is and who sees it, so an NPC's name tag
     * reads exactly like a hologram.
     *
     * @param label where the section is, for warnings, e.g. {@code npcs.yml: shop.name}
     */
    public static void readAppearance(ConfigurationNode node, HologramData data, String label) throws DataException {
        List<String> lines = lines(node);
        data.lines(lines);
        data.frames(frames(node, lines));
        readLook(node, data, label);
        data.viewDistance(node.node("view-distance").getDouble(HologramData.DEFAULT_VIEW_DISTANCE));
        data.updateIntervalTicks(node.node("update-interval").getInt(HologramData.AUTOMATIC_UPDATE_INTERVAL));
        data.lineSpacing(node.node("line-spacing").getDouble(HologramData.DEFAULT_LINE_SPACING));
        data.brightness(node.node("brightness-block").getInt(HologramData.LIGHT_FROM_WORLD),
                node.node("brightness-sky").getInt(HologramData.LIGHT_FROM_WORLD));
        data.shadowRadius(node.node("shadow-radius").getDouble(0));
        data.shadowStrength(node.node("shadow-strength").getDouble(1));
    }

    /** Writes what {@link #readAppearance} reads. */
    public static void writeAppearance(HologramData data, ConfigurationNode node, boolean withLines)
            throws SerializationException {
        if (withLines) {
            node.node("lines").setList(String.class, data.lines());
            if (data.animated()) {
                ConfigurationNode frames = node.node("frames");
                for (List<String> frame : data.frames()) {
                    frames.appendListNode().setList(String.class, frame);
                }
            }
        }
        node.node("scale").set(data.scale());
        node.node("billboard").set(data.billboard().configName());
        node.node("alignment").set(data.alignment().configName());
        node.node("background").set(data.background() == null ? DEFAULT : hex(data.background()));
        node.node("text-shadow").set(data.textShadow());
        node.node("see-through").set(data.seeThrough());
        node.node("view-distance").set(data.viewDistance());
        node.node("update-interval").set(data.updateIntervalTicks());
        node.node("line-spacing").set(data.lineSpacing());
        node.node("brightness-block").set(data.brightnessBlock());
        node.node("brightness-sky").set(data.brightnessSky());
        node.node("shadow-radius").set(data.shadowRadius());
        node.node("shadow-strength").set(data.shadowStrength());
    }

    private static Pos position(ConfigurationNode node) throws DataException {
        if (node.virtual()) {
            throw new DataException("'position' is missing (it needs x, y and z)");
        }
        return new Pos(number(node, "x"), number(node, "y"), number(node, "z"),
                (float) node.node("yaw").getDouble(0), (float) node.node("pitch").getDouble(0));
    }

    private static double number(ConfigurationNode position, String key) throws DataException {
        if (!(position.node(key).raw() instanceof Number value)) {
            throw new DataException("'position." + key + "' must be a number");
        }
        return value.doubleValue();
    }

    private static List<String> lines(ConfigurationNode node) throws DataException {
        List<String> lines = DataNodes.stringList(node.node("lines"));
        if (lines.size() > HologramData.MAX_LINES) {
            throw new DataException("'lines' has " + lines.size() + " lines; at most "
                    + HologramData.MAX_LINES + " are allowed");
        }
        return lines;
    }

    /** The animation frames, if any. The lines are always the first frame. */
    private static List<List<String>> frames(ConfigurationNode node, List<String> lines) {
        List<List<String>> frames = new ArrayList<>();
        frames.add(lines);
        for (ConfigurationNode frame : node.node("frames").childrenList()) {
            List<String> frameLines = DataNodes.stringList(frame);
            // The file may repeat the first frame under "frames"; then it is not an extra one.
            if (!frameLines.isEmpty() && !frameLines.equals(lines)) {
                frames.add(frameLines);
            }
        }
        return frames;
    }

    private static void readMaterials(ConfigurationNode node, HologramData data, HologramType type)
            throws DataException {
        String itemName = node.node("item").getString("");
        String blockName = node.node("block").getString("");
        if (!itemName.isBlank()) {
            data.item(material(itemName));
        }
        if (!blockName.isBlank()) {
            data.block(block(blockName));
        }
        if (type == HologramType.ITEM && data.item() == null) {
            throw new DataException("an item hologram needs 'item', for example \"item: diamond\"");
        }
        if (type == HologramType.BLOCK && data.block() == null) {
            throw new DataException("a block hologram needs 'block', for example \"block: diamond_block\"");
        }
    }

    private static void readLook(ConfigurationNode node, HologramData data, String label) {
        data.scale(node.node("scale").getDouble(1));
        Billboard billboard = Billboard.fromConfigName(node.node("billboard").getString(DEFAULT_BILLBOARD));
        if (billboard == null) {
            warn(label, "billboard", node.node("billboard").getString(""), Billboard.configNames());
        } else {
            data.billboard(billboard);
        }
        TextAlignment alignment = TextAlignment.fromConfigName(node.node("alignment").getString(DEFAULT_ALIGNMENT));
        if (alignment == null) {
            warn(label, "alignment", node.node("alignment").getString(""), TextAlignment.configNames());
        } else {
            data.alignment(alignment);
        }
        data.background(background(node.node("background").getString(DEFAULT), label));
        data.textShadow(node.node("text-shadow").getBoolean(false));
        data.seeThrough(node.node("see-through").getBoolean(false));
    }

    private static void readActions(ConfigurationNode node, HologramData data, String name) {
        List<Object> entries = DataNodes.entryList(node.node("actions"));
        long cooldown = Math.clamp(node.node("click-cooldown").getLong(DEFAULT_CLICK_COOLDOWN_MILLIS),
                0, MAX_CLICK_COOLDOWN_MILLIS);
        data.actions(entries,
                ActionParser.parseList(entries, cooldown, "holograms.yml: " + name + ".actions",
                        warning -> LOGGER.warn("{}", warning)));
    }

    private static Material material(String value) throws DataException {
        Material material = Material.fromKey(key(value));
        if (material == null) {
            throw new DataException("'item': there is no item called '" + value + "'");
        }
        return material;
    }

    private static Block block(String value) throws DataException {
        Block block = Block.fromKey(key(value));
        if (block == null) {
            throw new DataException("'block': there is no block called '" + value + "'");
        }
        return block;
    }

    private static String key(String value) {
        String name = value.strip().toLowerCase(Locale.ROOT);
        return name.contains(":") ? name : "minecraft:" + name;
    }

    /** {@code default}, {@code transparent}, {@code #RRGGBB} or {@code #AARRGGBB}. */
    private static @Nullable Integer background(String value, String label) {
        String text = value.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals(DEFAULT)) {
            return null;
        }
        if (text.equals(TRANSPARENT)) {
            return 0;
        }
        String digits = text.startsWith("#") ? text.substring(1) : text;
        try {
            if (digits.length() == RGB_LENGTH) {
                return OPAQUE | Integer.parseInt(digits, HEX);
            }
            if (digits.length() == ARGB_LENGTH) {
                return (int) Long.parseLong(digits, HEX);
            }
        } catch (NumberFormatException ignored) {
            // Reported below like any other unreadable value.
        }
        warn(label, "background", value, Set.of(DEFAULT, TRANSPARENT, "#RRGGBB", "#AARRGGBB"));
        return null;
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static void warn(String label, String option, String value, Set<String> allowed) {
        LOGGER.warn("{}.{} has the invalid value '{}'. Allowed: {}. Using the default.",
                label, option, value, String.join(", ", allowed.stream().sorted().toList()));
    }
}
