package io.github.mohammadhadimohammadi2007_dot.lobby.server.compat;

import net.minestom.server.entity.EntityType;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Warns about materials and entity types that do not exist in Minecraft 1.8.
 *
 * <p>Players on old clients reach the lobby through ViaRewind, which has to replace anything their client
 * does not know, so a menu item or NPC may look different to them than intended. Everything still works,
 * so this only logs a warning once per option, with the config option named.
 */
public final class LegacyItems {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyItems.class);
    private static final String MATERIALS_FILE = "/compat/minecraft-1-8-materials.txt";

    /** Entity types that exist in 1.8. Mobs and objects, by their modern name. */
    private static final Set<String> ENTITY_TYPES = Set.of(
            "item", "experience_orb", "egg", "leash_knot", "painting", "arrow", "snowball", "fireball",
            "small_fireball", "ender_pearl", "eye_of_ender", "potion", "experience_bottle", "item_frame",
            "wither_skull", "tnt", "falling_block", "firework_rocket", "armor_stand", "boat", "minecart",
            "chest_minecart", "furnace_minecart", "tnt_minecart", "hopper_minecart", "spawner_minecart",
            "command_block_minecart", "creeper", "skeleton", "spider", "giant", "zombie", "slime", "ghast",
            "zombified_piglin", "enderman", "cave_spider", "silverfish", "blaze", "magma_cube",
            "ender_dragon", "wither", "bat", "witch", "endermite", "guardian", "pig", "sheep", "cow",
            "chicken", "squid", "wolf", "mooshroom", "snow_golem", "ocelot", "iron_golem", "horse",
            "rabbit", "villager", "player", "fishing_bobber", "lightning_bolt");

    private static final Set<String> MATERIALS = loadMaterials();
    /** Options already warned about, so a reload does not repeat every warning. */
    private static final Set<String> WARNED = new HashSet<>();

    private LegacyItems() {
    }

    /** True if this material exists in Minecraft 1.8. */
    public static boolean exists(Material material) {
        return MATERIALS.contains(name(material.key().value()));
    }

    /** True if this entity type exists in Minecraft 1.8. */
    public static boolean exists(EntityType type) {
        return ENTITY_TYPES.contains(name(type.key().value()));
    }

    /**
     * Logs a warning (once per option) if {@code material} is unknown to 1.8 clients.
     *
     * @param option where it was set, e.g. {@code menus.yml: servers.items.bedwars.material}
     */
    public static void warnIfMissing(Material material, String option) {
        if (!exists(material)) {
            warn(option, material.key().value(), "item");
        }
    }

    /** Logs a warning (once per option) if {@code type} is unknown to 1.8 clients. */
    public static void warnIfMissing(EntityType type, String option) {
        if (!exists(type)) {
            warn(option, type.key().value(), "entity");
        }
    }

    /** Forgets which options were warned about, so a reload reports current problems again. */
    public static synchronized void resetWarnings() {
        WARNED.clear();
    }

    private static synchronized void warn(String option, String value, String kind) {
        if (!WARNED.add(option + "=" + value)) {
            return;
        }
        LOGGER.warn("{}: the {} '{}' does not exist in Minecraft 1.8, so players on old clients"
                + " (through ViaRewind) are shown a replacement. It still works for everyone else.",
                option, kind, value);
    }

    private static String name(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static Set<String> loadMaterials() {
        Set<String> names = new LinkedHashSet<>();
        try (InputStream in = LegacyItems.class.getResourceAsStream(MATERIALS_FILE)) {
            if (in == null) {
                LOGGER.warn("{} is missing from the jar, so 1.8 compatibility warnings are off", MATERIALS_FILE);
                return Set.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        names.add(name(trimmed));
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {}, so 1.8 compatibility warnings are off: {}", MATERIALS_FILE, e.getMessage());
            return Set.of();
        }
        return Set.copyOf(names);
    }

    /** Names in the bundled list that no longer exist in this Minecraft version. For the test only. */
    static List<String> unknownNames() {
        Set<String> known = new HashSet<>();
        for (Material material : Material.values()) {
            known.add(name(material.key().value()));
        }
        return MATERIALS.stream().filter(entry -> !known.contains(entry)).toList();
    }
}
