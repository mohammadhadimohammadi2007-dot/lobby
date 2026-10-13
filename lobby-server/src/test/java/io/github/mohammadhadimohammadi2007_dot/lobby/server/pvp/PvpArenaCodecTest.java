package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.DataException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpArenaCodecTest {

    @BeforeAll
    static void loadItems() {
        MinecraftServer.init();
    }

    @Test
    void anAreaWithItsKitIsWrittenAndReadBack() throws Exception {
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.SHARPNESS, 3))
                .with(DataComponents.CUSTOM_NAME, Component.text("Duel sword"));
        Kit kit = new Kit(Map.of(0, sword, 8, ItemStack.of(Material.GOLDEN_APPLE, 3)),
                Map.of(EquipmentSlot.HELMET, ItemStack.of(Material.IRON_HELMET),
                        EquipmentSlot.OFF_HAND, ItemStack.of(Material.SHIELD)));
        PvpArena arena = new PvpArena(new Region(PvpArena.OWNER, PvpArena.NAME, -5, 60, -5, 5, 70, 5),
                new Pos(1.5, 61, 2.5, 90, 10), kit);

        ConfigurationNode node = BasicConfigurationNode.root().node(PvpArena.NAME);
        PvpArenaCodec codec = new PvpArenaCodec();
        codec.write(arena, node);
        PvpArena read = codec.read(node);

        assertEquals(arena.region(), read.region());
        assertEquals(arena.spawn(), read.spawn());
        assertEquals(kit, read.kit(), "every item keeps its data");
        assertEquals(3, read.kit().items().get(0).get(DataComponents.ENCHANTMENTS).level(Enchantment.SHARPNESS));
        assertEquals(3, read.kit().items().get(8).amount());
    }

    @Test
    void withoutASpawnPlayersComeBackInTheMiddle() throws Exception {
        ConfigurationNode node = BasicConfigurationNode.root().node(PvpArena.NAME);
        node.node("from").setList(Integer.class, java.util.List.of(0, 60, 0));
        node.node("to").setList(Integer.class, java.util.List.of(10, 70, 20));
        PvpArena arena = new PvpArenaCodec().read(node);
        assertNull(arena.spawn());
        assertTrue(arena.kit().isEmpty());
        assertEquals(new Pos(5.5, 60, 10.5), arena.respawn());
    }

    @Test
    void brokenEntriesAreExplained() throws Exception {
        ConfigurationNode node = BasicConfigurationNode.root().node(PvpArena.NAME);
        node.node("from").setList(Integer.class, java.util.List.of(0, 60));
        assertTrue(assertThrows(DataException.class, () -> new PvpArenaCodec().read(node)).getMessage().contains("from"));

        node.node("from").setList(Integer.class, java.util.List.of(0, 60, 0));
        node.node("to").setList(Integer.class, java.util.List.of(1, 61, 1));
        node.node("kit").node("99").set("{id: \"minecraft:stone\", count: 1}");
        assertTrue(assertThrows(DataException.class, () -> new PvpArenaCodec().read(node)).getMessage()
                .contains("99"));
    }

    @Test
    void leaderboardPlaceholdersAreParsed() {
        PvpPlaceholders placeholders = new PvpPlaceholders(null);
        assertTrue(placeholders.lookup("top_best_streak_1_name") != null);
        assertTrue(placeholders.lookup("top_kills_10_value") != null);
        assertNull(placeholders.lookup("top_kills_name"));
        assertNull(placeholders.lookup("top_wins_1_name"));
        assertNull(placeholders.lookup("top_kills_0_name"));
        assertNull(placeholders.lookup("top_kills_1_score"));
    }
}