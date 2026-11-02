package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * cosmetics.yml: the trails, hats, gadgets, pets, balloons and outfits players can choose, and the limits that keep
 * particles cheap.
 * A cosmetic that cannot be read is reported by name and left out; the others still work.
 *
 * @param viewDistance  players further away (blocks) do not get someone's particles and gadget effects
 * @param perViewer     most cosmetic particle packets one player receives per tick
 * @param perTick       most cosmetic particle packets the whole server sends per tick
 * @param previewMillis how long a preview from the menu lasts
 * @param showLocked    true to list cosmetics a player has not unlocked (marked as locked)
 * @param cosmetics     by type, in file order
 */
public record CosmeticsConfig(boolean enabled, double viewDistance, int perViewer, int perTick, long previewMillis,
                              boolean showLocked, Map<CosmeticType, List<Cosmetic>> cosmetics) {

    /** The most of one type, so they fit a menu page with a row of buttons below. */
    public static final int MAX_PER_TYPE = 45;
    private static final int MAX_AMOUNT = 20;
    private static final int MAX_COOLDOWN_SECONDS = 3600;
    private static final int MAX_RANGE = 64;
    private static final List<String> DEFAULT_PAINT = List.of("red_wool", "orange_wool", "yellow_wool", "lime_wool",
            "light_blue_wool", "magenta_wool");
    /** The option name of each armor slot in an outfit. */
    private static final Map<String, EquipmentSlot> OUTFIT_SLOTS = Map.of("helmet", EquipmentSlot.HELMET,
            "chest", EquipmentSlot.CHESTPLATE, "legs", EquipmentSlot.LEGGINGS, "feet", EquipmentSlot.BOOTS);

    public CosmeticsConfig {
        Map<CosmeticType, List<Cosmetic>> copy = new EnumMap<>(CosmeticType.class);
        for (CosmeticType type : CosmeticType.values()) {
            copy.put(type, List.copyOf(cosmetics.getOrDefault(type, List.of())));
        }
        cosmetics = Map.copyOf(copy);
    }

    /** The cosmetics of one type, in file order. */
    public List<Cosmetic> of(CosmeticType type) {
        return cosmetics.get(type);
    }

    /** The cosmetic of this type and id, or {@code null}. */
    public @Nullable Cosmetic find(CosmeticType type, @Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Cosmetic cosmetic : cosmetics.get(type)) {
            if (cosmetic.id().equals(id)) {
                return cosmetic;
            }
        }
        return null;
    }

    /** How many cosmetics there are in all. */
    public int count() {
        return cosmetics.values().stream().mapToInt(List::size).sum();
    }

    /** Reads cosmetics.yml. */
    public static CosmeticsConfig read(ConfigReader reader) {
        Map<CosmeticType, List<Cosmetic>> cosmetics = new EnumMap<>(CosmeticType.class);
        for (CosmeticType type : CosmeticType.values()) {
            List<Cosmetic> list = new ArrayList<>();
            for (String key : reader.keys(type.section())) {
                String path = type.section() + "." + key;
                String id = key.strip().toLowerCase(Locale.ROOT);
                if (!CosmeticChoices.ID.matcher(id).matches()) {
                    reader.reportInvalid(path, key, "a name of letters, digits, _ and - (at most 32)");
                    continue;
                }
                if (list.size() == MAX_PER_TYPE) {
                    reader.addWarning(reader.fileName() + ": at most " + MAX_PER_TYPE + " " + type.section()
                            + " fit the menu; '" + key + "' and the ones after it are left out.");
                    break;
                }
                Cosmetic cosmetic = cosmetic(reader, type, id, path);
                if (cosmetic != null) {
                    list.add(cosmetic);
                }
            }
            cosmetics.put(type, list);
        }
        return new CosmeticsConfig(
                reader.bool("enabled", true),
                reader.decimal("view-distance", 4, 128),
                reader.integer("limits.per-viewer", 1, 1000),
                reader.integer("limits.per-tick", 1, 100_000),
                reader.integer("preview-seconds", 1, 60) * 1000L,
                reader.bool("show-locked", true),
                cosmetics);
    }

    private static @Nullable Cosmetic cosmetic(ConfigReader reader, CosmeticType type, String id, String path) {
        String name = reader.string(path + ".name", id);
        List<String> lore = reader.stringList(path + ".lore", List.of());
        String permission = reader.string(path + ".permission",
                "lobby.cosmetic." + type.configName() + "." + id).strip();
        String version = reader.string(path + ".min-version", "").strip();
        int minProtocol = 0;
        if (!version.isEmpty()) {
            minProtocol = ProtocolVersions.fromName(version);
            if (minProtocol < 0) {
                reader.reportInvalid(path + ".min-version", version, "a Minecraft version like \"1.9\", or \"\"");
                return null;
            }
        }
        Cosmetic.Look look = switch (type) {
            case TRAIL -> trail(reader, path);
            case HAT -> hat(reader, path, minProtocol);
            case GADGET -> gadget(reader, path);
            case PET -> pet(reader, path);
            case BALLOON -> balloon(reader, path, minProtocol);
            case OUTFIT -> outfit(reader, path, minProtocol);
        };
        if (look == null) {
            return null;
        }
        Material icon = switch (look) {
            case Cosmetic.Hat hat -> hat.material();
            case Cosmetic.Balloon balloon -> balloon.look().material();
            case Cosmetic.Trail ignored -> icon(reader, path, "blaze_powder", minProtocol);
            case Cosmetic.Pet ignored -> icon(reader, path, "bone", minProtocol);
            case Cosmetic.Outfit ignored -> icon(reader, path, "leather_chestplate", minProtocol);
            case Cosmetic.Gadget gadget -> icon(reader, path, switch (gadget.type()) {
                case FIREWORK -> "firework_rocket";
                case LEAP -> "feather";
                case BURST -> "nether_star";
                case PAINTBALL -> "snowball";
                case GRAPPLE -> "fishing_rod";
                case CONFETTI -> "paper";
                case TNT -> "tnt";
                case LIGHTNING -> "blaze_rod";
            }, minProtocol);
        };
        return icon == null ? null : new Cosmetic(type, id, name, lore, icon, permission, minProtocol, look);
    }

    private static @Nullable Material icon(ConfigReader reader, String path, String fallback, int minProtocol) {
        return material(reader, path + ".icon", reader.string(path + ".icon", fallback), minProtocol);
    }

    /**
     * The material, or {@code null} with a warning. Items unknown to 1.8 are only warned about when 1.8 players can
     * see the cosmetic.
     */
    private static @Nullable Material material(ConfigReader reader, String option, String name, int minProtocol) {
        if (minProtocol <= ProtocolVersions.V1_8) {
            return MenuConfig.material(reader, option, name);
        }
        String key = name.strip().toLowerCase(Locale.ROOT);
        Material material = Material.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (material == null) {
            reader.reportInvalid(option, name, "an item name like \"diamond_sword\"");
        }
        return material;
    }

    private static Cosmetic.@Nullable Trail trail(ConfigReader reader, String path) {
        Particle particle = particle(reader, path + ".particle", reader.string(path + ".particle", ""));
        if (particle == null) {
            return null;
        }
        String color = reader.string(path + ".color", "").strip();
        if (!color.isEmpty()) {
            TextColor parsed = color(color);
            if (parsed == null) {
                reader.reportInvalid(path + ".color", color, "a color like \"#ff5555\" or \"red\"");
            } else if (particle instanceof Particle.Dust dust) {
                particle = dust.withColor(parsed);
            } else {
                reader.addWarning(reader.fileName() + ": '" + path + ".color' only works with the particle \"dust\";"
                        + " it is ignored here.");
            }
        }
        String styleName = reader.string(path + ".style", "feet").strip().toUpperCase(Locale.ROOT);
        Cosmetic.TrailStyle style;
        try {
            style = Cosmetic.TrailStyle.valueOf(styleName);
        } catch (IllegalArgumentException e) {
            reader.reportInvalid(path + ".style", styleName.toLowerCase(Locale.ROOT), Arrays.stream(
                    Cosmetic.TrailStyle.values()).map(Cosmetic.TrailStyle::configName).toList().toString());
            return null;
        }
        String dripName = reader.string(path + ".drip", "").strip();
        Particle drip = null;
        if (!dripName.isEmpty()) {
            if (style != Cosmetic.TrailStyle.CLOUD) {
                reader.addWarning(reader.fileName() + ": '" + path + ".drip' only works with style: cloud;"
                        + " it is ignored here.");
            } else {
                drip = particle(reader, path + ".drip", dripName);
            }
        }
        return new Cosmetic.Trail(particle, reader.integer(path + ".amount", 1, MAX_AMOUNT, 2),
                reader.decimal(path + ".spread", 0, 2, 0.1), reader.decimal(path + ".speed", 0, 1, 0), style, drip);
    }

    private static Cosmetic.@Nullable Pet pet(ConfigReader reader, String path) {
        String name = reader.string(path + ".entity", "").strip().toLowerCase(Locale.ROOT);
        EntityType entity = name.isEmpty() ? null : EntityType.fromKey(name.contains(":") ? name : "minecraft:" + name);
        if (entity == null || !PetTypes.living(entity)) {
            reader.reportInvalid(path + ".entity", name, "an animal or mob like \"wolf\"");
            return null;
        }
        // Animals without babies are simply shown grown up.
        boolean baby = reader.bool(path + ".baby", true) && PetTypes.ageable(entity);
        return new Cosmetic.Pet(entity, baby, reader.string(path + ".name-tag", ""));
    }

    private static Cosmetic.@Nullable Balloon balloon(ConfigReader reader, String path, int minProtocol) {
        Cosmetic.Hat look = hat(reader, path, minProtocol);
        return look == null ? null : new Cosmetic.Balloon(look, reader.bool(path + ".leash", true));
    }

    private static Cosmetic.@Nullable Outfit outfit(ConfigReader reader, String path, int minProtocol) {
        Map<EquipmentSlot, Material> pieces = new EnumMap<>(EquipmentSlot.class);
        for (String option : List.of("helmet", "chest", "legs", "feet")) {
            String name = reader.string(path + "." + option, "").strip();
            if (name.isEmpty()) {
                continue;
            }
            Material material = material(reader, path + "." + option, name, minProtocol);
            if (material == null) {
                return null;
            }
            pieces.put(OUTFIT_SLOTS.get(option), material);
        }
        if (pieces.isEmpty()) {
            reader.reportInvalid(path, "", "at least one of helmet, chest, legs and feet");
            return null;
        }
        String colorName = reader.string(path + ".color", "").strip();
        TextColor color = null;
        if (!colorName.isEmpty()) {
            color = color(colorName);
            if (color == null) {
                reader.reportInvalid(path + ".color", colorName, "a color like \"#ff5555\" or \"red\"");
            } else if (pieces.values().stream().noneMatch(Cosmetic.Outfit::dyeable)) {
                reader.addWarning(reader.fileName() + ": '" + path + ".color' only dyes leather armor;"
                        + " it is ignored here.");
            }
        }
        return new Cosmetic.Outfit(pieces, color);
    }

    /** {@code material}, with a head's {@code texture} (a skin value) or {@code head-owner} (a player name). */
    private static Cosmetic.@Nullable Hat hat(ConfigReader reader, String path, int minProtocol) {
        Material material = material(reader, path + ".material", reader.string(path + ".material", "player_head"),
                minProtocol);
        if (material == null) {
            return null;
        }
        String texture = reader.string(path + ".texture", "").strip();
        String owner = reader.string(path + ".head-owner", "").strip();
        if (material != Material.PLAYER_HEAD) {
            if (!texture.isEmpty() || !owner.isEmpty()) {
                reader.addWarning(reader.fileName() + ": '" + path + "' sets texture or head-owner, which only works"
                        + " with material: player_head; they are ignored.");
            }
            return new Cosmetic.Hat(material, null);
        }
        if (!texture.isEmpty()) {
            return new Cosmetic.Hat(material, new ResolvableProfile(new PlayerSkin(texture, null)));
        }
        if (!owner.isEmpty()) {
            if (!owner.matches("[A-Za-z0-9_]{1,16}")) {
                reader.reportInvalid(path + ".head-owner", owner, "a Minecraft player name");
                return null;
            }
            // The client looks the skin up itself, as for a player head placed in the world.
            return new Cosmetic.Hat(material, new ResolvableProfile(new ResolvableProfile.Partial(owner, null, List.of())));
        }
        return new Cosmetic.Hat(material, null);
    }

    private static Cosmetic.@Nullable Gadget gadget(ConfigReader reader, String path) {
        String typeName = reader.string(path + ".type", "").strip().toUpperCase(Locale.ROOT);
        Cosmetic.GadgetType type;
        try {
            type = Cosmetic.GadgetType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            reader.reportInvalid(path + ".type", typeName.toLowerCase(Locale.ROOT), Arrays.stream(
                    Cosmetic.GadgetType.values()).map(Cosmetic.GadgetType::configName).toList().toString());
            return null;
        }
        long cooldown = reader.integer(path + ".cooldown", 1, MAX_COOLDOWN_SECONDS, 10) * 1000L;
        boolean usesParticle = switch (type) {
            case LEAP, BURST, GRAPPLE -> true;
            default -> false;
        };
        String particleName = reader.string(path + ".particle", switch (type) {
            case LEAP -> "cloud";
            case GRAPPLE -> "crit";
            default -> "heart";
        });
        Particle particle = !usesParticle || particleName.isBlank() ? null
                : particle(reader, path + ".particle", particleName);
        SoundEvent sound = sound(reader, path + ".sound", reader.string(path + ".sound", ""));
        double power = reader.decimal(path + ".power", type == Cosmetic.GadgetType.TNT ? 0 : 0.1, 5, switch (type) {
            case LEAP -> 1.2;
            case PAINTBALL -> 1.5;
            case BURST -> 1.5;
            default -> 1;
        });
        int range = reader.integer(path + ".range", 3, MAX_RANGE, type == Cosmetic.GadgetType.GRAPPLE ? 25 : 30);
        long duration = reader.integer(path + ".duration", 1, 60, 5) * 1000L;
        List<Block> blocks = new ArrayList<>();
        if (type == Cosmetic.GadgetType.PAINTBALL) {
            for (String name : reader.stringList(path + ".blocks", DEFAULT_PAINT)) {
                String key = name.strip().toLowerCase(Locale.ROOT);
                Block block = Block.fromKey(key.contains(":") ? key : "minecraft:" + key);
                if (block == null || !block.solid()) {
                    reader.reportInvalid(path + ".blocks", name, "solid blocks like \"red_wool\"");
                } else {
                    blocks.add(block);
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(Block.RED_WOOL);
            }
        }
        List<RGBLike> colors = new ArrayList<>();
        for (String name : reader.stringList(path + ".colors", List.of("#ff5555", "#ffff55", "#55ffff"))) {
            TextColor color = color(name);
            if (color == null) {
                reader.reportInvalid(path + ".colors", name, "colors like \"#ff5555\" or \"red\"");
            } else {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            colors.add(NamedTextColor.WHITE);
        }
        String shapeName = reader.string(path + ".shape", "large_ball").strip().toUpperCase(Locale.ROOT);
        FireworkExplosion.Shape shape;
        try {
            shape = FireworkExplosion.Shape.valueOf(shapeName);
        } catch (IllegalArgumentException e) {
            reader.reportInvalid(path + ".shape", shapeName.toLowerCase(Locale.ROOT), Arrays.stream(
                    FireworkExplosion.Shape.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).toList().toString());
            shape = FireworkExplosion.Shape.LARGE_BALL;
        }
        return new Cosmetic.Gadget(type, cooldown, power, particle, sound, colors, shape, range, duration, blocks);
    }

    private static @Nullable Particle particle(ConfigReader reader, String option, String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        Particle particle = key.isEmpty() ? null : Particle.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (particle == null) {
            reader.reportInvalid(option, name, "a particle name like \"flame\"");
        }
        return particle;
    }

    private static @Nullable SoundEvent sound(ConfigReader reader, String option, String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return null;
        }
        SoundEvent sound = SoundEvent.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (sound == null) {
            reader.reportInvalid(option, name, "a sound name like \"entity.player.levelup\", or \"\"");
        }
        return sound;
    }

    private static @Nullable TextColor color(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        return value.startsWith("#") ? TextColor.fromHexString(value) : NamedTextColor.NAMES.value(value);
    }
}
