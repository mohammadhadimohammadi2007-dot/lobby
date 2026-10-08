package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataNodes;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports holograms from a FancyHolograms {@code holograms.yml} that the admin copied into
 * {@code <data folder>/import/}.
 *
 * <p>It is deliberately forgiving: FancyHolograms has written its file in more than one shape over the
 * years, so every option is looked for in the places it has lived ({@code location} or {@code position},
 * {@code data.text} or {@code text}) and anything missing falls back to this lobby's default. What it
 * could not read is reported by name instead of guessed, so nothing is imported wrongly in silence.
 *
 * <p>The world in the file is ignored: holograms belong to the lobby map here.
 */
public final class FancyHologramsImport {

    /** Where the file is looked for, in order. */
    private static final List<String> FILE_NAMES = List.of("fancyholograms.yml", "holograms.yml");
    private static final String IMPORT_FOLDER = "import";
    private static final int TICKS_PER_SECOND = 20;

    /**
     * What one import did.
     *
     * @param file     the file that was read, even if it does not exist
     * @param imported the holograms that were created
     * @param skipped  entries that could not be read, with the reason
     */
    public record Result(Path file, List<HologramData> imported, List<String> skipped) {

        public Result {
            imported = List.copyOf(imported);
            skipped = List.copyOf(skipped);
        }
    }

    private FancyHologramsImport() {
    }

    /** The file an admin should copy their FancyHolograms holograms.yml to. */
    public static Path expectedFile(Path dataDir) {
        return dataDir.resolve(IMPORT_FOLDER).resolve(FILE_NAMES.getLast());
    }

    /**
     * Reads the file and creates every hologram that is not there yet. Blocking (it reads a file), so
     * run it off the tick thread.
     */
    public static Result run(Path dataDir, HologramService holograms) {
        Path file = findFile(dataDir);
        List<HologramData> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (file == null) {
            return new Result(expectedFile(dataDir), imported, skipped);
        }
        ConfigurationNode root;
        try {
            root = YamlConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException e) {
            skipped.add(file.getFileName() + " is not valid YAML: " + e.getMessage());
            return new Result(file, imported, skipped);
        }
        ConfigurationNode entries = root.node("holograms").virtual() ? root : root.node("holograms");
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : entries.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            try {
                HologramData data = read(name, entry.getValue(), holograms);
                if (data == null) {
                    skipped.add(name + " (a hologram with that name already exists here)");
                } else {
                    imported.add(data);
                }
            } catch (RuntimeException e) {
                skipped.add(name + " (" + e.getMessage() + ")");
            }
        }
        return new Result(file, imported, skipped);
    }

    private static @Nullable HologramData read(String name, ConfigurationNode node, HologramService holograms) {
        if (!HologramService.validName(name)) {
            throw new IllegalArgumentException("the name has characters this lobby does not allow");
        }
        ConfigurationNode data = node.node("data").virtual() ? node : node.node("data");
        HologramType type = type(node, data);
        List<String> lines = lines(data);
        HologramData created = holograms.create(name, type, position(node), lines);
        if (created == null) {
            return null;
        }
        apply(created, data, type);
        holograms.changed(created);
        return created;
    }

    private static HologramType type(ConfigurationNode node, ConfigurationNode data) {
        String name = node.node("type").getString(data.node("type").getString("text"));
        HologramType type = HologramType.fromConfigName(name);
        if (type == null) {
            throw new IllegalArgumentException("unknown type '" + name + "'");
        }
        return type;
    }

    private static Pos position(ConfigurationNode node) {
        ConfigurationNode location = node.node("location").virtual() ? node.node("position") : node.node("location");
        if (location.virtual()) {
            throw new IllegalArgumentException("it has no location");
        }
        if (!(location.node("x").raw() instanceof Number x) || !(location.node("y").raw() instanceof Number y)
                || !(location.node("z").raw() instanceof Number z)) {
            throw new IllegalArgumentException("its location has no x, y and z");
        }
        return new Pos(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private static List<String> lines(ConfigurationNode data) {
        for (String key : List.of("text", "lines")) {
            if (!data.node(key).virtual() && data.node(key).isList()) {
                return DataNodes.stringList(data.node(key));
            }
        }
        return List.of();
    }

    /** Copies the options FancyHolograms and this lobby both have; the rest keeps our default. */
    private static void apply(HologramData hologram, ConfigurationNode data, HologramType type) {
        Billboard billboard = Billboard.fromConfigName(data.node("billboard").getString("center"));
        if (billboard != null) {
            hologram.billboard(billboard);
        }
        TextAlignment alignment = TextAlignment.fromConfigName(
                data.node("text_alignment").getString(data.node("alignment").getString("center")));
        if (alignment != null) {
            hologram.alignment(alignment);
        }
        hologram.textShadow(data.node("text_shadow").getBoolean(data.node("text-shadow").getBoolean(false)));
        hologram.seeThrough(data.node("see_through").getBoolean(data.node("see-through").getBoolean(false)));
        hologram.scale(data.node("scale").getDouble(1));
        double distance = data.node("visibility_distance").getDouble(data.node("view-distance")
                .getDouble(HologramData.DEFAULT_VIEW_DISTANCE));
        hologram.viewDistance(distance);
        // FancyHolograms counts the text update interval in seconds, -1 meaning "never".
        int seconds = data.node("update_text_interval").getInt(Integer.MIN_VALUE);
        if (seconds != Integer.MIN_VALUE) {
            hologram.updateIntervalTicks(seconds <= 0 ? 0 : seconds * TICKS_PER_SECOND);
        }
        String background = data.node("background").getString("");
        if (!background.isBlank()) {
            HologramProperties.apply(hologram, "background", background);
        }
        if (type == HologramType.ITEM) {
            Material item = Material.fromKey(key(data.node("item").getString("stone")));
            hologram.item(item == null ? Material.STONE : item);
        }
        if (type == HologramType.BLOCK) {
            Block block = Block.fromKey(key(data.node("block").getString("stone")));
            hologram.block(block == null ? Block.STONE : block);
        }
    }

    private static String key(String value) {
        String name = value.strip().toLowerCase(Locale.ROOT);
        return name.contains(":") ? name : "minecraft:" + name;
    }

    private static @Nullable Path findFile(Path dataDir) {
        for (String name : FILE_NAMES) {
            Path file = dataDir.resolve(IMPORT_FOLDER).resolve(name);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }
}
