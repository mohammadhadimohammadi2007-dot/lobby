package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.kyori.adventure.util.RGBLike;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One cosmetic from cosmetics.yml.
 *
 * @param id          its name in the file, lower case; with the type, what is saved for players
 * @param name        shown in the menu and in messages, MiniMessage
 * @param lore        lines under the name in the menu
 * @param icon        the menu item; for a hat, balloon or outfit, the menu shows the item itself instead
 * @param permission  needed to use it; empty for everyone
 * @param minProtocol the oldest client protocol that can show it; older clients do not see it in the menu
 * @param look        what it is: a {@link Trail}, {@link Hat}, {@link Gadget}, {@link Pet}, {@link Balloon} or
 *                    {@link Outfit}, matching the type
 */
public record Cosmetic(CosmeticType type, String id, String name, List<String> lore, Material icon, String permission,
                       int minProtocol, Look look) {

    /** The settings of one kind of cosmetic. */
    public sealed interface Look permits Trail, Hat, Gadget, Pet, Balloon, Outfit {
    }

    public Cosmetic {
        lore = List.copyOf(lore);
        boolean matches = switch (type) {
            case TRAIL -> look instanceof Trail;
            case HAT -> look instanceof Hat;
            case GADGET -> look instanceof Gadget;
            case PET -> look instanceof Pet;
            case BALLOON -> look instanceof Balloon;
            case OUTFIT -> look instanceof Outfit;
        };
        if (!matches) {
            throw new IllegalArgumentException("a " + type.configName() + " needs its " + type.configName() + " settings");
        }
    }

    public @Nullable Trail trail() {
        return look instanceof Trail trail ? trail : null;
    }

    public @Nullable Hat hat() {
        return look instanceof Hat hat ? hat : null;
    }

    public @Nullable Gadget gadget() {
        return look instanceof Gadget gadget ? gadget : null;
    }

    public @Nullable Pet pet() {
        return look instanceof Pet pet ? pet : null;
    }

    public @Nullable Balloon balloon() {
        return look instanceof Balloon balloon ? balloon : null;
    }

    public @Nullable Outfit outfit() {
        return look instanceof Outfit outfit ? outfit : null;
    }

    /** The item shown in the menu: the hat, balloon or chest piece itself. Needs a running server (item components). */
    public ItemStack iconItem() {
        return switch (look) {
            case Hat hat -> hat.item();
            case Balloon balloon -> balloon.look().item();
            case Outfit outfit -> outfit.menuItem(icon);
            default -> ItemStack.of(icon);
        };
    }

    /** True if right-clicking it in the menu shows a preview (everything except what is worn). */
    public boolean previewable() {
        return type != CosmeticType.HAT && type != CosmeticType.OUTFIT;
    }

    /**
     * What a hat puts on the head; also what a balloon looks like.
     *
     * @param profile for a player head: whose skin it shows; {@code null} for any other item
     */
    public record Hat(Material material, @Nullable ResolvableProfile profile) implements Look {

        /** The worn item. Needs a running server (item components). */
        public ItemStack item() {
            ItemStack item = ItemStack.of(material);
            return profile == null ? item : item.with(DataComponents.PROFILE, profile);
        }
    }

    /** How the particles of a trail are placed around the player. */
    public enum TrailStyle {
        /** At the feet, only while walking: a trail on the ground behind the player. */
        FEET,
        /** A ring above the head, also while standing still. */
        HALO,
        /** Two points circling the body from the feet to the head, also while standing still. */
        SPIRAL,
        /** A turning ring on the ground around the feet, also while standing still. */
        RING,
        /** A pair of flapping wings on the back. */
        WINGS,
        /** A little cloud above the head, with drops falling from it ({@code drip}). */
        CLOUD;

        /** The name in cosmetics.yml. */
        public String configName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The particles of a trail.
     *
     * @param amount particles per burst
     * @param spread how far they scatter around their point, in blocks
     * @param speed  how fast they move, for particles that move
     * @param drip   {@link TrailStyle#CLOUD}: what falls from the cloud, or {@code null} for nothing
     */
    public record Trail(Particle particle, int amount, double spread, double speed, TrailStyle style,
                        @Nullable Particle drip) implements Look {

        public Trail(Particle particle, int amount, double spread, double speed, TrailStyle style) {
            this(particle, amount, spread, speed, style, null);
        }
    }

    /** What a gadget does. */
    public enum GadgetType {
        /** A firework rocket goes up from the player and explodes in the gadget's colors. */
        FIREWORK(false),
        /** Throws the player forward and up ({@code power}), with a puff of particles. */
        LEAP(false),
        /** A ring of particles bursts out around the player. */
        BURST(false),
        /** Paints the blocks where the player looks, for everyone nearby, for a few seconds. */
        PAINTBALL(true),
        /** Pulls the player to the block they look at. */
        GRAPPLE(true),
        /** Shoots colored confetti where the player looks. */
        CONFETTI(false),
        /** A primed TNT that explodes after a moment, pushing players away without breaking anything. */
        TNT(false),
        /** Lightning strikes the block the player looks at (or the player, if they look at nothing). */
        LIGHTNING(false);

        private final boolean needsTarget;

        GadgetType(boolean needsTarget) {
            this.needsTarget = needsTarget;
        }

        /** True if it does nothing (and costs no cooldown) when the player looks at no block in range. */
        public boolean needsTarget() {
            return needsTarget;
        }

        public String configName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A gadget.
     *
     * @param cooldownMillis the shortest time between two uses by the same player
     * @param power          {@link GadgetType#LEAP}: how far it throws; {@link GadgetType#BURST}: the ring's radius;
     *                       {@link GadgetType#PAINTBALL}: the splash's radius; {@link GadgetType#GRAPPLE}: how hard
     *                       it pulls; {@link GadgetType#CONFETTI}: how much confetti; {@link GadgetType#TNT}: how hard
     *                       it pushes (0 = not at all)
     * @param particle       {@link GadgetType#LEAP}, {@link GadgetType#BURST} and {@link GadgetType#GRAPPLE}: the
     *                       particle, or {@code null}
     * @param sound          played where it is used, or {@code null}
     * @param colors         {@link GadgetType#FIREWORK} and {@link GadgetType#CONFETTI}: the colors
     * @param shape          {@link GadgetType#FIREWORK}: the explosion's shape
     * @param range          gadgets that aim: the farthest block they reach
     * @param durationMillis {@link GadgetType#PAINTBALL}: how long the paint stays
     * @param blocks         {@link GadgetType#PAINTBALL}: the paint, one picked at random per block
     */
    public record Gadget(GadgetType type, long cooldownMillis, double power, @Nullable Particle particle,
                         @Nullable SoundEvent sound, List<RGBLike> colors, FireworkExplosion.Shape shape, int range,
                         long durationMillis, List<Block> blocks) implements Look {

        public Gadget {
            colors = List.copyOf(colors);
            blocks = List.copyOf(blocks);
        }

        /** A gadget with the defaults for the options only some types use. */
        public Gadget(GadgetType type, long cooldownMillis, double power, @Nullable Particle particle,
                      @Nullable SoundEvent sound, List<RGBLike> colors, FireworkExplosion.Shape shape) {
            this(type, cooldownMillis, power, particle, sound, colors, shape, 30, 5000, List.of());
        }
    }

    /**
     * A pet: an animal shown only with packets, walking after the player.
     *
     * @param entity  what animal
     * @param baby    shown as a baby (for animals that have babies)
     * @param nameTag the name above it (MiniMessage and placeholders, for the owner), empty for none
     */
    public record Pet(EntityType entity, boolean baby, String nameTag) implements Look {
    }

    /**
     * A balloon floating above the player.
     *
     * @param look  the block or head it is
     * @param leash true to tie it to the player's hand with a lead
     */
    public record Balloon(Hat look, boolean leash) implements Look {
    }

    /**
     * Armor worn as clothes.
     *
     * @param pieces by slot: the item; a helmet is only worn when the player has no hat
     * @param color  the dye of leather pieces, or {@code null} for their own color
     */
    public record Outfit(Map<EquipmentSlot, Material> pieces, @Nullable RGBLike color) implements Look {

        public Outfit {
            pieces = Map.copyOf(pieces);
        }

        /** The worn items by slot. Needs a running server (item components). */
        public Map<EquipmentSlot, ItemStack> items() {
            Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
            pieces.forEach((slot, material) -> items.put(slot, piece(material)));
            return items;
        }

        private ItemStack piece(Material material) {
            ItemStack item = ItemStack.of(material);
            return color != null && dyeable(material) ? item.with(DataComponents.DYED_COLOR, color) : item;
        }

        /** The chest piece (or any piece) for the menu, else {@code fallback}. */
        ItemStack menuItem(Material fallback) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS,
                    EquipmentSlot.HELMET)) {
                Material material = pieces.get(slot);
                if (material != null) {
                    return piece(material);
                }
            }
            return ItemStack.of(fallback);
        }

        /** True for leather armor, which can be dyed. */
        static boolean dyeable(Material material) {
            return material.key().value().startsWith("leather_");
        }
    }
}
