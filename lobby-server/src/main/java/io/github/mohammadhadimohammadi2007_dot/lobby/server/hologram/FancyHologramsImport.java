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
 * <p>The keys are the ones FancyHolograms itself writes (checked against its {@code HologramData},
 * {@code DisplayHologramData}, {@code TextHologramData}, {@code ItemHologramData} and
 * {@code BlockHologramData}): {@code location.*}, {@code text}, {@code text_alignment},
 * {@code text_shadow}, {@code see_through}, {@code background}, {@code billboard},
 * {@code scale_x/y/z}, {@code translation_x/y/z}, {@code shadow_radius}, {@code shadow_strength},
 * {@code block_brightness}, {@code sky_brightness}, {@code visibility_distance}, {@code visibility},
 * {@code update_text_interval} (milliseconds there, ticks here), {@code item}, {@code block} and
 * {@code linkedNpc}.
 *
 * <p>A hologram linked to a FancyNpcs NPC becomes that NPC's name tag instead of a hologram of its own,
 * so the NPCs have to be imported first. What could not be read or mapped is reported by name instead of
 * guessed, and a hologram whose name already exists is never overwritten.
 */
public final class FancyHologramsImport {

    /** Where the file is looked for, in order. */
    private static final List<String> FILE_NAMES = List.of("fancyholograms.yml", "holograms.yml");
    private static final String IMPORT_FOLDER = "import";
    private static final double MILLIS_PER_TICK = 50;
    /** Scales closer than this count as the same, so a uniform scale is not reported as uneven. */
    private static final double SCALE_TOLERANCE = 1e-3;

    /**
     * What one import did.
     *
     * @param file      the file that was read, even if it does not exist
     * @param imported  the holograms that were created
     * @param nameTags  NPCs whose name tag was set from a linked hologram
     * @param skipped   entries that could not be read or mapped, with the reason
     */
    public record Result(Path file, List<HologramData> imported, List<String> nameTags, List<String> skipped) {

        public Result {
            imported = List.copyOf(imported);
            nameTags = List.copyOf(nameTags);
            skipped = List.copyOf(skipped);
        }
    }

    private FancyHologramsImport() {
    }

    /** The file an admin should copy their FancyHolograms holograms.yml to. */
    public static Path expectedFile(Path dataDir) {
        return dataDir.resolve(IMPORT_FOLDER).resolve(FILE_NAMES.getLast());
    }

    /** Imports without NPCs: linked holograms are reported. */
    public static Result run(Path dataDir, HologramService holograms) {
        return run(dataDir, holograms, NameTags.NONE);
    }

    /**
     * Reads the file and creates every hologram that is not there yet. Blocking (it reads a file), so run
     * it off the tick thread.
     */
    public static Result run(Path dataDir, HologramService holograms, NameTags nameTags) {
        Path file = findFile(dataDir);
        List<HologramData> imported = new ArrayList<>();
        List<String> linked = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (file == null) {
            return new Result(expectedFile(dataDir), imported, linked, skipped);
        }
        ConfigurationNode root;
        try {
            root = YamlConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException e) {
            skipped.add(file.getFileName() + " is not valid YAML: " + e.getMessage());
            return new Result(file, imported, linked, skipped);
        }
        ConfigurationNode entries = root.node("holograms").virtual() ? root : root.node("holograms");
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : entries.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            ConfigurationNode node = entry.getValue();
            try {
                String npc = node.node("linkedNpc").getString("");
                if (!npc.isBlank()) {
                    linkToNpc(name, node, npc, nameTags, linked, skipped);
                    continue;
                }
                HologramData data = read(name, node, holograms, skipped);
                if (data == null) {
                    skipped.add(name + " (a hologram with that name already exists here)");
                } else {
                    imported.add(data);
                }
            } catch (RuntimeException e) {
                skipped.add(name + " (" + e.getMessage() + ")");
            }
        }
        return new Result(file, imported, linked, skipped);
    }

    private static @Nullable HologramData read(String name, ConfigurationNode node, HologramService holograms,
                                               List<String> notes) {
        if (!HologramService.validName(name)) {
            throw new IllegalArgumentException("the name has characters this lobby does not allow");
        }
        HologramType type = HologramType.fromConfigName(node.node("type").getString("text"));
        if (type == null) {
            throw new IllegalArgumentException("unknown type '" + node.node("type").getString() + "'");
        }
        HologramData created = holograms.create(name, type, position(node), DataNodes.stringList(node.node("text")));
        if (created == null) {
            return null;
        }
        applyLook(created, node, name, notes);
        applyTranslation(created, node);
        applyVisibility(created, node);
        if (type == HologramType.ITEM) {
            created.item(item(node.node("item")));
        }
        if (type == HologramType.BLOCK) {
            Block block = Block.fromKey(key(node.node("block").getString("grass_block")));
            created.block(block == null ? Block.GRASS_BLOCK : block);
        }
        holograms.changed(created);
        return created;
    }

    /** A linked hologram: its lines and look go onto the NPC's name tag. */
    private static void linkToNpc(String name, ConfigurationNode node, String npc, NameTags nameTags,
                                  List<String> linked, List<String> skipped) {
        String npcName = npc.toLowerCase(Locale.ROOT);
        HologramData nameTag = nameTags.nameTagOf(npcName);
        if (nameTag == null) {
            skipped.add(name + " (linked to the NPC '" + npc + "', which does not exist here. Import the NPCs"
                    + " first with /npc import, then the holograms)");
            return;
        }
        nameTag.frames(List.of(DataNodes.stringList(node.node("text"))));
        applyLook(nameTag, node, name, skipped);
        nameTags.changed(npcName);
        linked.add(npcName);
    }

    /** Everything about how a FancyHolograms hologram looks, in FancyHolograms' own keys. */
    private static void applyLook(HologramData data, ConfigurationNode node, String name, List<String> notes) {
        Billboard billboard = Billboard.fromConfigName(node.node("billboard").getString("center"));
        if (billboard != null) {
            data.billboard(billboard);
        }
        TextAlignment alignment = TextAlignment.fromConfigName(node.node("text_alignment").getString("center"));
        if (alignment != null) {
            data.alignment(alignment);
        }
        data.textShadow(node.node("text_shadow").getBoolean(false));
        data.seeThrough(node.node("see_through").getBoolean(false));
        data.scale(scale(node, name, notes));
        data.viewDistance(node.node("visibility_distance").getDouble(HologramData.DEFAULT_VIEW_DISTANCE));
        data.shadowRadius(node.node("shadow_radius").getDouble(0));
        data.shadowStrength(node.node("shadow_strength").getDouble(1));
        data.brightness(node.node("block_brightness").getInt(HologramData.LIGHT_FROM_WORLD),
                node.node("sky_brightness").getInt(HologramData.LIGHT_FROM_WORLD));
        String background = node.node("background").getString("");
        if (!background.isBlank()) {
            HologramProperties.apply(data, "background", background);
        }
        // FancyHolograms counts in milliseconds, -1 meaning never; this lobby counts ticks, 0 meaning never.
        int millis = node.node("update_text_interval").getInt(-1);
        data.updateIntervalTicks(millis < 0 ? 0 : (int) Math.max(1, Math.round(millis / MILLIS_PER_TICK)));
    }

    /**
     * A translation only moves the drawing away from the position, so it is added to the position. Not
     * for a name tag, whose position always follows its NPC.
     */
    private static void applyTranslation(HologramData data, ConfigurationNode node) {
        double dx = node.node("translation_x").getDouble(0);
        double dy = node.node("translation_y").getDouble(0);
        double dz = node.node("translation_z").getDouble(0);
        if (dx != 0 || dy != 0 || dz != 0) {
            data.position(data.position().add(dx, dy, dz));
        }
    }

    /** One scale for all three axes; an uneven one is made even and reported. */
    private static double scale(ConfigurationNode node, String name, List<String> notes) {
        double x = node.node("scale_x").getDouble(1);
        double y = node.node("scale_y").getDouble(1);
        double z = node.node("scale_z").getDouble(1);
        if (Math.abs(x - y) > SCALE_TOLERANCE || Math.abs(y - z) > SCALE_TOLERANCE) {
            notes.add(name + ": its scale differs per axis (" + x + ", " + y + ", " + z + "); it was set to "
                    + y + " on all three, because this lobby scales evenly");
        }
        return y;
    }

    /** ALL, PERMISSION_REQUIRED and MANUAL, as FancyHolograms names them. */
    private static void applyVisibility(HologramData data, ConfigurationNode node) {
        String visibility = node.node("visibility").getString("ALL").toUpperCase(Locale.ROOT);
        data.visibility(switch (visibility) {
            case "PERMISSION_REQUIRED" -> HologramVisibility.PERMISSION;
            case "MANUAL" -> HologramVisibility.MANUAL;
            default -> HologramVisibility.ALL;
        });
    }

    /**
     * The item of an item hologram. Bukkit writes an item as a section: older servers with
     * {@code type: DIAMOND_SWORD}, newer ones with {@code id: minecraft:diamond_sword}.
     */
    private static Material item(ConfigurationNode node) {
        // raw() only: asking a whole item section for a text default replaces the section with it.
        Object written = node.isMap() ? (node.node("id").raw() != null ? node.node("id").raw() : node.node("type").raw())
                : node.raw();
        String name = written == null ? "stone" : String.valueOf(written);
        Material material = Material.fromKey(key(name));
        return material == null ? Material.STONE : material;
    }

    private static Pos position(ConfigurationNode node) {
        ConfigurationNode location = node.node("location");
        if (location.virtual()) {
            throw new IllegalArgumentException("it has no location");
        }
        if (!(location.node("x").raw() instanceof Number x) || !(location.node("y").raw() instanceof Number y)
                || !(location.node("z").raw() instanceof Number z)) {
            throw new IllegalArgumentException("its location has no x, y and z");
        }
        return new Pos(x.doubleValue(), y.doubleValue(), z.doubleValue(),
                (float) location.node("yaw").getDouble(0), (float) location.node("pitch").getDouble(0));
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
