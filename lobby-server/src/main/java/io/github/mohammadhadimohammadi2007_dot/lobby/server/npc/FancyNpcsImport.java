package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports NPCs from a FancyNpcs {@code npcs.yml} that the admin copied into {@code <data folder>/import/}.
 *
 * <p>The keys are the ones FancyNpcs itself writes (checked against its {@code NpcManagerImpl}):
 * {@code npcs.<id>.name}, {@code displayName} ({@code <empty>} for none), {@code type},
 * {@code location.*}, {@code skin.identifier}, {@code skin.mirrorSkin}, the older {@code skin.value} and
 * {@code skin.signature}, {@code glowing}, {@code glowingColor}, {@code showInTab}, {@code scale},
 * {@code turnToPlayer}, {@code turnToPlayerDistance}, {@code interactionCooldown} (seconds),
 * {@code visibility_distance}, {@code equipment.<SLOT>}, {@code attributes.pose} and
 * {@code actions.<TRIGGER>.<order>.action|value}. {@code collidable} is not read: NPCs made of packets
 * never collide with anyone.
 *
 * <p>The world is ignored, as NPCs here belong to the lobby map. What could not be mapped is reported by
 * name, and an NPC whose name already exists is never overwritten. Skins that are looked up (a player
 * name or a MineSkin link) are fetched in the background once the NPC exists.
 */
public final class FancyNpcsImport {

    private static final List<String> FILE_NAMES = List.of("fancynpcs.yml", "npcs.yml");
    private static final String IMPORT_FOLDER = "import";
    private static final String EMPTY_NAME = "<empty>";
    private static final long MILLIS_PER_SECOND = 1000;

    /** FancyNpcs' equipment slots and the ones Minecraft (and this lobby) calls them. */
    private static final Map<String, EquipmentSlot> SLOTS = Map.of(
            "MAINHAND", EquipmentSlot.MAIN_HAND,
            "OFFHAND", EquipmentSlot.OFF_HAND,
            "HEAD", EquipmentSlot.HELMET,
            "CHEST", EquipmentSlot.CHESTPLATE,
            "LEGS", EquipmentSlot.LEGGINGS,
            "FEET", EquipmentSlot.BOOTS);

    /**
     * What one import did.
     *
     * @param file     the file that was read, even if it does not exist
     * @param imported the NPCs that were created
     * @param skipped  NPCs or parts of NPCs that could not be mapped, with the reason
     */
    public record Result(Path file, List<NpcData> imported, List<String> skipped) {

        public Result {
            imported = List.copyOf(imported);
            skipped = List.copyOf(skipped);
        }
    }

    private FancyNpcsImport() {
    }

    /** The file an admin should copy their FancyNpcs npcs.yml to. */
    public static Path expectedFile(Path dataDir) {
        return dataDir.resolve(IMPORT_FOLDER).resolve(FILE_NAMES.getLast());
    }

    /**
     * Reads the file and creates every NPC that is not there yet. Blocking (it reads a file), so run it
     * off the tick thread.
     */
    public static Result run(Path dataDir, NpcService npcs) {
        Path file = findFile(dataDir);
        List<NpcData> imported = new ArrayList<>();
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
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("npcs").childrenMap().entrySet()) {
            ConfigurationNode node = entry.getValue();
            String name = node.node("name").getString(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
            try {
                NpcData data = read(name, node, npcs, skipped);
                if (data == null) {
                    skipped.add(name + " (an NPC with that name already exists here)");
                } else {
                    imported.add(data);
                }
            } catch (IllegalArgumentException | DataException e) {
                skipped.add(name + " (" + e.getMessage() + ")");
            }
        }
        return new Result(file, imported, skipped);
    }

    private static @Nullable NpcData read(String name, ConfigurationNode node, NpcService npcs, List<String> notes)
            throws DataException {
        if (!NpcService.validName(name)) {
            throw new IllegalArgumentException("the name has characters this lobby does not allow");
        }
        Pos position = position(node.node("location"));
        NpcData data = npcs.create(name, position);
        if (data == null) {
            return null;
        }
        data.type(NpcCodec.entityType(node.node("type").getString("player")));
        String displayName = node.node("displayName").getString(EMPTY_NAME);
        data.nameTag().frames(List.of(displayName.equalsIgnoreCase(EMPTY_NAME) ? List.of() : List.of(displayName)));
        data.glowing(node.node("glowing").getBoolean(false));
        readGlowColor(node.node("glowingColor"), data, name, notes);
        data.showInTab(data.isPlayer() && node.node("showInTab").getBoolean(false));
        data.scale(node.node("scale").getDouble(1));
        readAttributes(node.node("attributes"), data, name, notes);
        data.turnToPlayer(node.node("turnToPlayer").getBoolean(false));
        double turnDistance = node.node("turnToPlayerDistance").getDouble(0);
        if (turnDistance > 0) {
            data.turnDistance(turnDistance);
        }
        data.viewDistance(node.node("visibility_distance").getDouble(NpcData.DEFAULT_VIEW_DISTANCE));
        readEquipment(node.node("equipment"), data, name, notes);
        data.clickCooldownMillis(Math.round(node.node("interactionCooldown").getDouble(0) * MILLIS_PER_SECOND));
        readActions(node.node("actions"), data, name, npcs, notes);
        npcs.changed(data);
        readSkin(node.node("skin"), data, name, npcs, notes);
        return data;
    }

    private static Pos position(ConfigurationNode location) {
        if (location.virtual() || !(location.node("x").raw() instanceof Number x)
                || !(location.node("y").raw() instanceof Number y) || !(location.node("z").raw() instanceof Number z)) {
            throw new IllegalArgumentException("its location has no x, y and z");
        }
        return new Pos(x.doubleValue(), y.doubleValue(), z.doubleValue(),
                (float) location.node("yaw").getDouble(0), (float) location.node("pitch").getDouble(0));
    }

    /** {@code skin.mirrorSkin}, then {@code skin.value}/{@code signature}, then {@code skin.identifier}. */
    private static void readSkin(ConfigurationNode skin, NpcData data, String name, NpcService npcs,
                                 List<String> notes) {
        if (!data.isPlayer()) {
            return;
        }
        if (skin.node("mirrorSkin").getBoolean(false)) {
            npcs.setSkin(data, NpcSkin.MIRROR);
            return;
        }
        String value = skin.node("value").getString("");
        String signature = skin.node("signature").getString("");
        if (!value.isBlank() && !signature.isBlank()) {
            data.resolvedSkin(new PlayerSkin(value, signature));
            npcs.setSkin(data, NpcSkin.TEXTURE);
            return;
        }
        String identifier = skin.node("identifier").getString("");
        if (identifier.isBlank()) {
            return;
        }
        try {
            NpcSkin parsed = NpcSkin.parse(identifier);
            if (parsed.kind() == NpcSkin.Kind.IMAGE && !npcs.canUploadSkins()) {
                notes.add(name + ": its skin '" + identifier + "' was not kept: " + NpcSkins.NO_KEY_EXPLANATION);
                return;
            }
            npcs.setSkin(data, parsed);
        } catch (NpcSkin.SkinException e) {
            notes.add(name + ": its skin '" + identifier + "' was not kept: " + e.getMessage());
        }
    }

    /**
     * Bukkit writes an item as a section: older servers with {@code type: DIAMOND_SWORD}, newer ones
     * with {@code id: minecraft:diamond_sword}.
     */
    private static void readEquipment(ConfigurationNode equipment, NpcData data, String name, List<String> notes) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : equipment.childrenMap().entrySet()) {
            String slotName = String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT);
            EquipmentSlot slot = SLOTS.get(slotName);
            ConfigurationNode item = entry.getValue();
            // raw() only: asking a whole item section for a text default replaces the section with it.
            boolean hasDetails = !item.node("meta").virtual() || !item.node("components").virtual();
            Object written = item.isMap() ? firstNonNull(item.node("id").raw(), item.node("type").raw()) : item.raw();
            String itemName = written == null ? "" : String.valueOf(written);
            String key = itemName.strip().toLowerCase(Locale.ROOT);
            Material material = key.isEmpty() ? null : Material.fromKey(key.contains(":") ? key : "minecraft:" + key);
            if (slot == null || material == null) {
                notes.add(name + ": the equipment " + slotName + " (" + itemName + ") was not kept");
                continue;
            }
            data.equipment(slot, material);
            if (hasDetails) {
                notes.add(name + ": the equipment " + slotName + " is kept as a plain " + material.key().value()
                        + "; its enchantments, colour, name or head texture were not");
            }
        }
    }

    private static @Nullable Object firstNonNull(@Nullable Object first, @Nullable Object second) {
        return first != null ? first : second;
    }

    /** FancyNpcs writes the colour's Adventure name, for example {@code dark_purple}. */
    private static void readGlowColor(ConfigurationNode node, NpcData data, String name, List<String> notes) {
        String written = node.getString("white");
        TeamColor color = NpcCodec.glowColor(written);
        if (color == null) {
            notes.add(name + ": its glowing colour '" + written + "' is not one of Minecraft's 16; it glows white");
            return;
        }
        data.glowColor(color);
    }

    /**
     * {@code attributes.<name>}: the pose is kept; the other attributes FancyNpcs has (on_fire, invisible,
     * the per-mob variants...) are reported by name.
     */
    private static void readAttributes(ConfigurationNode attributes, NpcData data, String name, List<String> notes) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : attributes.childrenMap().entrySet()) {
            String attribute = String.valueOf(entry.getKey());
            String value = entry.getValue().getString("");
            if (value.isBlank()) {
                continue;
            }
            if (attribute.equalsIgnoreCase("pose")) {
                NpcPose pose = NpcPose.fromName(value);
                if (pose == null || !data.pose(pose)) {
                    notes.add(name + ": its pose '" + value + "' was not kept");
                }
                continue;
            }
            notes.add(name + ": its attribute " + attribute + "=" + value + " was not kept; this lobby has no"
                    + " such NPC setting");
        }
    }

    /** {@code actions.<TRIGGER>.<order>.action|value}, translated by {@link FancyNpcsActions}. */
    private static void readActions(ConfigurationNode actions, NpcData data, String name, NpcService npcs,
                                    List<String> notes) {
        for (Map.Entry<Object, ? extends ConfigurationNode> triggerEntry : actions.childrenMap().entrySet()) {
            String triggerName = String.valueOf(triggerEntry.getKey());
            NpcTrigger trigger = NpcTrigger.fromCommandName(triggerName);
            if (trigger == null) {
                notes.add(name + ": the " + triggerName + " actions were not kept; that trigger is only for"
                        + " other plugins' code");
                continue;
            }
            List<FancyNpcsActions.Action> list = triggerEntry.getValue().childrenMap().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> order(entry.getKey())))
                    .map(entry -> new FancyNpcsActions.Action(entry.getValue().node("action").getString(""),
                            entry.getValue().node("value").getString("")))
                    .toList();
            List<String> problems = new ArrayList<>();
            List<Object> entries = FancyNpcsActions.list(list, problems);
            for (String problem : problems) {
                notes.add(name + " (" + trigger.commandName() + "): " + problem);
            }
            if (!entries.isEmpty()) {
                npcs.setActions(data, trigger, entries);
            }
        }
    }

    private static int order(Object key) {
        try {
            return Integer.parseInt(String.valueOf(key));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
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
