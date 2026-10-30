package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.kyori.adventure.util.RGBLike;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * One cosmetic from cosmetics.yml.
 *
 * @param id          its name in the file, lower case; with the type, what is saved for players
 * @param name        shown in the menu and in messages, MiniMessage
 * @param lore        lines under the name in the menu
 * @param icon        the menu item; for a hat, the hat's material (the menu shows the hat itself)
 * @param permission  needed to use it; empty for everyone
 * @param minProtocol the oldest client protocol that can show it; older clients do not see it in the menu
 * @param trail       for a trail: its particles
 * @param hat         for a hat: what is put on the head
 * @param gadget      for a gadget: what it does
 */
public record Cosmetic(CosmeticType type, String id, String name, List<String> lore, Material icon, String permission,
                       int minProtocol, @Nullable Trail trail, @Nullable Hat hat, @Nullable Gadget gadget) {

    public Cosmetic {
        lore = List.copyOf(lore);
        Objects.requireNonNull(switch (type) {
            case TRAIL -> trail;
            case HAT -> hat;
            case GADGET -> gadget;
        }, () -> "a " + type.configName() + " needs its " + type.configName() + " settings");
    }

    /** The item shown in the menu: the hat itself for a hat. Needs a running server (item components). */
    public ItemStack iconItem() {
        return hat != null ? hat.item() : ItemStack.of(icon);
    }

    /** True if right-clicking it in the menu shows a preview (trails and gadgets). */
    public boolean previewable() {
        return type != CosmeticType.HAT;
    }

    /**
     * What a hat puts on the head.
     *
     * @param profile for a player head: whose skin it shows; {@code null} for any other item
     */
    public record Hat(Material material, @Nullable ResolvableProfile profile) {

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
        SPIRAL;

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
     */
    public record Trail(Particle particle, int amount, double spread, double speed, TrailStyle style) {
    }

    /** What a gadget does. */
    public enum GadgetType {
        /** A firework rocket goes up from the player and explodes in the gadget's colors. */
        FIREWORK,
        /** Throws the player forward and up ({@code power}), with a puff of particles. */
        LEAP,
        /** A ring of particles bursts out around the player. */
        BURST;

        public String configName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A gadget.
     *
     * @param cooldownMillis the shortest time between two uses by the same player
     * @param power          {@link GadgetType#LEAP}: how far it throws; {@link GadgetType#BURST}: the ring's radius
     * @param particle       {@link GadgetType#LEAP} and {@link GadgetType#BURST}: the particle, or {@code null}
     * @param sound          played where it is used, or {@code null}
     * @param colors         {@link GadgetType#FIREWORK}: the explosion's colors
     * @param shape          {@link GadgetType#FIREWORK}: the explosion's shape
     */
    public record Gadget(GadgetType type, long cooldownMillis, double power, @Nullable Particle particle,
                         @Nullable SoundEvent sound, List<RGBLike> colors, FireworkExplosion.Shape shape) {

        public Gadget {
            colors = List.copyOf(colors);
        }
    }
}
