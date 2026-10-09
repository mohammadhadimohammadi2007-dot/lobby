package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.EntityPart;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the entities of one hologram.
 *
 * <p>Modern clients (1.19.4 and newer) get one display entity: a text display holds every line at once
 * and can be scaled, coloured and turned. Older clients have no display entities, so they get the
 * classic lobby trick instead: one invisible armor stand per line of text, or a floating item for item
 * and block holograms. Both are packets only; nothing of this exists in the world.
 */
public final class HologramParts {

    /** Minecraft's own translucent background, which is what a text display uses by default. */
    static final int DEFAULT_BACKGROUND = 0x40000000;
    private static final byte FLAG_SHADOW = 0x01;
    private static final byte FLAG_SEE_THROUGH = 0x02;
    private static final byte FLAG_DEFAULT_BACKGROUND = 0x04;
    /** Entity flags: 0x20 is "invisible". */
    private static final byte ENTITY_INVISIBLE = 0x20;
    /** Armor stand flags: 0x01 small, 0x08 no base plate, 0x10 marker (no hitbox). */
    private static final byte ARMOR_STAND_HIDDEN = 0x01 | 0x08 | 0x10;
    /** A client stops drawing a display entity past {@code VIEW_RANGE * 64} blocks. */
    private static final double CLIENT_RANGE_PER_UNIT = 64;
    /** A block display is drawn from its corner, so it is moved half a block to look centred. */
    private static final double BLOCK_CENTRE = 0.5;
    /** A dropped item floats a little above the point it is spawned at. */
    private static final double ITEM_DROP_OFFSET = 0.25;
    /** Minecraft packs the two light levels of a display entity into one number. */
    private static final int BLOCK_LIGHT_SHIFT = 4;
    private static final int SKY_LIGHT_SHIFT = 20;

    private HologramParts() {
    }

    /** What modern clients see: one display entity. */
    public static List<EntityPart> modern(HologramData data, List<Component> lines) {
        return switch (data.type()) {
            case TEXT -> List.of(new EntityPart(EntityType.TEXT_DISPLAY, data.position(), textMetadata(data, lines)));
            case ITEM -> List.of(new EntityPart(EntityType.ITEM_DISPLAY, data.position(), itemDisplayMetadata(data)));
            case BLOCK -> List.of(new EntityPart(EntityType.BLOCK_DISPLAY, centredForBlock(data), blockMetadata(data)));
        };
    }

    /** What clients older than 1.19.4 see: armor stands and dropped items. */
    public static List<EntityPart> legacy(HologramData data, List<Component> lines) {
        if (data.type() != HologramType.TEXT) {
            return List.of(new EntityPart(EntityType.ITEM, data.position().sub(0, ITEM_DROP_OFFSET, 0),
                    Map.of(MetadataDef.ItemEntity.ITEM.index(), Metadata.ItemStack(legacyItem(data)),
                            MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true))));
        }
        List<EntityPart> parts = new ArrayList<>(lines.size());
        for (int line = 0; line < lines.size(); line++) {
            // The first line goes on top, so the lines read downwards like they do on a modern client.
            double offset = (lines.size() - 1 - line) * data.lineSpacing();
            parts.add(new EntityPart(EntityType.ARMOR_STAND, data.position().add(0, offset, 0),
                    armorStandMetadata(lines.get(line))));
        }
        return parts;
    }

    private static Map<Integer, Metadata.Entry<?>> textMetadata(HologramData data, List<Component> lines) {
        Map<Integer, Metadata.Entry<?>> metadata = displayMetadata(data);
        metadata.put(MetadataDef.TextDisplay.TEXT.index(),
                Metadata.Component(Component.join(JoinConfiguration.newlines(), lines)));
        metadata.put(MetadataDef.TextDisplay.TEXT_DISPLAY_FLAGS.index(), Metadata.Byte(textFlags(data)));
        metadata.put(MetadataDef.TextDisplay.BACKGROUND_COLOR.index(),
                Metadata.VarInt(data.background() == null ? DEFAULT_BACKGROUND : data.background()));
        return metadata;
    }

    private static Map<Integer, Metadata.Entry<?>> itemDisplayMetadata(HologramData data) {
        Map<Integer, Metadata.Entry<?>> metadata = displayMetadata(data);
        metadata.put(MetadataDef.ItemDisplay.DISPLAYED_ITEM.index(), Metadata.ItemStack(legacyItem(data)));
        return metadata;
    }

    private static Map<Integer, Metadata.Entry<?>> blockMetadata(HologramData data) {
        Map<Integer, Metadata.Entry<?>> metadata = displayMetadata(data);
        if (data.block() != null) {
            metadata.put(MetadataDef.BlockDisplay.DISPLAYED_BLOCK_STATE.index(), Metadata.BlockState(data.block()));
        }
        return metadata;
    }

    /** What every display entity shares: how big it is, how it turns, and how far it is drawn. */
    private static Map<Integer, Metadata.Entry<?>> displayMetadata(HologramData data) {
        Map<Integer, Metadata.Entry<?>> metadata = new LinkedHashMap<>();
        metadata.put(MetadataDef.Display.BILLBOARD_CONSTRAINTS.index(), Metadata.Byte(data.billboard().value()));
        if (data.scale() != 1) {
            metadata.put(MetadataDef.Display.SCALE.index(),
                    Metadata.Vector3(new Vec(data.scale(), data.scale(), data.scale())));
        }
        // Without this the client stops drawing it at 64 blocks, however far the server sends it.
        float range = (float) Math.max(1, data.viewDistance() / CLIENT_RANGE_PER_UNIT);
        metadata.put(MetadataDef.Display.VIEW_RANGE.index(), Metadata.Float(range));
        if (data.brightnessBlock() != HologramData.LIGHT_FROM_WORLD
                || data.brightnessSky() != HologramData.LIGHT_FROM_WORLD) {
            metadata.put(MetadataDef.Display.BRIGHTNESS_OVERRIDE.index(), Metadata.VarInt(brightness(data)));
        }
        if (data.shadowRadius() > 0) {
            metadata.put(MetadataDef.Display.SHADOW_RADIUS.index(), Metadata.Float((float) data.shadowRadius()));
            metadata.put(MetadataDef.Display.SHADOW_STRENGTH.index(), Metadata.Float((float) data.shadowStrength()));
        }
        return metadata;
    }

    /** The two light levels as one number; a level left at "from the world" becomes full light. */
    private static int brightness(HologramData data) {
        int block = data.brightnessBlock() == HologramData.LIGHT_FROM_WORLD
                ? HologramData.MAX_LIGHT : data.brightnessBlock();
        int sky = data.brightnessSky() == HologramData.LIGHT_FROM_WORLD
                ? HologramData.MAX_LIGHT : data.brightnessSky();
        return (block << BLOCK_LIGHT_SHIFT) | (sky << SKY_LIGHT_SHIFT);
    }

    private static byte textFlags(HologramData data) {
        int flags = data.alignment().bits();
        if (data.textShadow()) {
            flags |= FLAG_SHADOW;
        }
        if (data.seeThrough()) {
            flags |= FLAG_SEE_THROUGH;
        }
        if (data.background() == null) {
            flags |= FLAG_DEFAULT_BACKGROUND;
        }
        return (byte) flags;
    }

    private static Map<Integer, Metadata.Entry<?>> armorStandMetadata(Component line) {
        return Map.of(
                MetadataDef.ENTITY_FLAGS.index(), Metadata.Byte(ENTITY_INVISIBLE),
                MetadataDef.CUSTOM_NAME.index(), Metadata.OptComponent(line),
                MetadataDef.CUSTOM_NAME_VISIBLE.index(), Metadata.Boolean(true),
                MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true),
                MetadataDef.ArmorStand.ARMOR_STAND_FLAGS.index(), Metadata.Byte(ARMOR_STAND_HIDDEN));
    }

    /** The item shown by an item hologram, or the block's item form for a block hologram. */
    private static ItemStack legacyItem(HologramData data) {
        Material material = data.item();
        if (material == null && data.block() != null) {
            material = Material.fromKey(data.block().key());
        }
        return material == null ? ItemStack.of(Material.STONE) : ItemStack.of(material);
    }

    private static Pos centredForBlock(HologramData data) {
        double half = BLOCK_CENTRE * data.scale();
        return data.position().sub(half, 0, half);
    }
}
