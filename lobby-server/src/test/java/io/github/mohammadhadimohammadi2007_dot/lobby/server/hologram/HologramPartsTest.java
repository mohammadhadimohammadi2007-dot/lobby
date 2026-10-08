package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the two renderers really send: one display entity, or armor stands for old clients. */
class HologramPartsTest {

    private static final Pos AT = new Pos(10.5, 70, -4.5);
    /** The two lines of the test hologram as one text display shows them. */
    private static final String TWO_LINES = "first\nsecond";

    private static HologramData text(String... lines) {
        return new HologramData("welcome", HologramType.TEXT, AT, List.of(lines));
    }

    private static List<Component> components(String... lines) {
        return List.of(lines).stream().map(Component::text).map(Component.class::cast).toList();
    }

    @Test
    void modernTextIsOneDisplayEntityWithEveryLine() {
        HologramData data = text("first", "second");

        var parts = HologramParts.modern(data, components("first", "second"));

        assertEquals(1, parts.size(), "a text display holds every line itself");
        assertEquals(EntityType.TEXT_DISPLAY, parts.getFirst().type());
        assertEquals(AT, parts.getFirst().position());
        Metadata.Entry<?> shown = parts.getFirst().metadata().get(MetadataDef.TextDisplay.TEXT.index());
        assertEquals(TWO_LINES, PlainTextComponentSerializer.plainText()
                .serialize((Component) shown.value()), "the lines are joined with newlines");
    }

    @Test
    void textFlagsCarryShadowSeeThroughAndAlignment() {
        HologramData data = text("hi");
        int flagsIndex = MetadataDef.TextDisplay.TEXT_DISPLAY_FLAGS.index();

        // Default: no shadow, not see-through, Minecraft's own background (bit 0x04), centred.
        byte flags = (byte) HologramParts.modern(data, components("hi")).getFirst().metadata().get(flagsIndex).value();
        assertEquals(0x04, flags);

        data.textShadow(true);
        data.seeThrough(true);
        data.alignment(TextAlignment.LEFT);
        data.background(0xFF112233);
        flags = (byte) HologramParts.modern(data, components("hi")).getFirst().metadata().get(flagsIndex).value();
        // 0x01 shadow + 0x02 see-through + left alignment in the bits above, and no default background.
        assertEquals(0x01 | 0x02 | TextAlignment.LEFT.bits(), flags);
        assertEquals(0xFF112233, HologramParts.modern(data, components("hi")).getFirst()
                .metadata().get(MetadataDef.TextDisplay.BACKGROUND_COLOR.index()).value());
    }

    @Test
    void scaleAndViewRangeAreOnlySentWhenTheyMatter() {
        HologramData data = text("hi");
        var metadata = HologramParts.modern(data, components("hi")).getFirst().metadata();
        assertTrue(metadata.get(MetadataDef.Display.SCALE.index()) == null, "scale 1 needs no packet");
        assertEquals(1f, metadata.get(MetadataDef.Display.VIEW_RANGE.index()).value());

        data.scale(2);
        data.viewDistance(128);
        metadata = HologramParts.modern(data, components("hi")).getFirst().metadata();
        assertEquals(2.0, ((net.minestom.server.coordinate.Point) metadata
                .get(MetadataDef.Display.SCALE.index()).value()).y());
        // The client stops drawing a display past VIEW_RANGE * 64 blocks, so 128 blocks needs 2.
        assertEquals(2f, metadata.get(MetadataDef.Display.VIEW_RANGE.index()).value());
    }

    @Test
    void legacyTextIsOneArmorStandPerLineFromTheTopDown() {
        HologramData data = text("first", "second", "third");
        data.lineSpacing(0.3);

        var parts = HologramParts.legacy(data, components("first", "second", "third"));

        assertEquals(3, parts.size());
        assertEquals(List.of(EntityType.ARMOR_STAND, EntityType.ARMOR_STAND, EntityType.ARMOR_STAND),
                parts.stream().map(part -> part.type()).toList());
        // The first line is the highest, so the hologram reads downwards like on a modern client.
        assertEquals(AT.y() + 0.6, parts.getFirst().position().y(), 1e-6);
        assertEquals(AT.y() + 0.3, parts.get(1).position().y(), 1e-6);
        assertEquals(AT.y(), parts.get(2).position().y(), 1e-6);
        assertEquals(Component.text("second"),
                parts.get(1).metadata().get(MetadataDef.CUSTOM_NAME.index()).value());
        assertEquals(Boolean.TRUE,
                parts.get(1).metadata().get(MetadataDef.CUSTOM_NAME_VISIBLE.index()).value());
        // 0x20 is "invisible" in the entity flags; the stand itself must not be seen.
        assertEquals((byte) 0x20, parts.getFirst().metadata().get(MetadataDef.ENTITY_FLAGS.index()).value());
        // 0x01 small + 0x08 no base plate + 0x10 marker (no hitbox).
        assertEquals((byte) (0x01 | 0x08 | 0x10),
                parts.getFirst().metadata().get(MetadataDef.ArmorStand.ARMOR_STAND_FLAGS.index()).value());
    }

    @Test
    void itemAndBlockHologramsFallBackToADroppedItem() {
        HologramData item = new HologramData("shop", HologramType.ITEM, AT, List.of());
        item.item(Material.DIAMOND_SWORD);

        var modern = HologramParts.modern(item, List.of());
        assertEquals(EntityType.ITEM_DISPLAY, modern.getFirst().type());
        assertEquals(ItemStack.of(Material.DIAMOND_SWORD),
                modern.getFirst().metadata().get(MetadataDef.ItemDisplay.DISPLAYED_ITEM.index()).value());

        var legacy = HologramParts.legacy(item, List.of());
        assertEquals(EntityType.ITEM, legacy.getFirst().type(), "old clients have no display entities");
        assertEquals(ItemStack.of(Material.DIAMOND_SWORD),
                legacy.getFirst().metadata().get(MetadataDef.ItemEntity.ITEM.index()).value());

        HologramData block = new HologramData("statue", HologramType.BLOCK, AT, List.of());
        block.block(Block.DIAMOND_BLOCK);
        var blockParts = HologramParts.modern(block, List.of());
        assertEquals(EntityType.BLOCK_DISPLAY, blockParts.getFirst().type());
        assertEquals(Block.DIAMOND_BLOCK,
                blockParts.getFirst().metadata().get(MetadataDef.BlockDisplay.DISPLAYED_BLOCK_STATE.index()).value());
        // A block display is drawn from its corner, so it is moved half a block to look centred.
        assertEquals(AT.x() - 0.5, blockParts.getFirst().position().x(), 1e-6);
        // On an old client the block becomes its item form.
        assertEquals(ItemStack.of(Material.DIAMOND_BLOCK),
                HologramParts.legacy(block, List.of()).getFirst()
                        .metadata().get(MetadataDef.ItemEntity.ITEM.index()).value());
    }
}
