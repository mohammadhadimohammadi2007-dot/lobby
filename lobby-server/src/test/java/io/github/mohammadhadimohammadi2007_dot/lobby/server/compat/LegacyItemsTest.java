package io.github.mohammadhadimohammadi2007_dot.lobby.server.compat;

import net.minestom.server.entity.EntityType;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyItemsTest {

    /** Catches typos in the hand-written list: every name must still exist in this Minecraft version. */
    @Test
    void everyListedMaterialExists() {
        assertEquals(List.of(), LegacyItems.unknownNames());
    }

    @Test
    void knowsWhatIsOldAndWhatIsNew() {
        assertTrue(LegacyItems.exists(Material.CHEST));
        assertTrue(LegacyItems.exists(Material.DIAMOND_SWORD));
        assertTrue(LegacyItems.exists(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        assertTrue(LegacyItems.exists(Material.PLAYER_HEAD));
        // Added after 1.8: shulker boxes (1.11), concrete (1.12), barrels (1.14), copper (1.17).
        assertFalse(LegacyItems.exists(Material.WHITE_SHULKER_BOX));
        assertFalse(LegacyItems.exists(Material.LIME_CONCRETE));
        assertFalse(LegacyItems.exists(Material.BARREL));
        assertFalse(LegacyItems.exists(Material.COPPER_INGOT));
    }

    @Test
    void knowsOldAndNewEntityTypes() {
        assertTrue(LegacyItems.exists(EntityType.ZOMBIE));
        assertTrue(LegacyItems.exists(EntityType.ARMOR_STAND));
        assertTrue(LegacyItems.exists(EntityType.VILLAGER));
        // Added after 1.8: shulker (1.11), text display (1.19.4), interaction (1.19.4), warden (1.19).
        assertFalse(LegacyItems.exists(EntityType.SHULKER));
        assertFalse(LegacyItems.exists(EntityType.TEXT_DISPLAY));
        assertFalse(LegacyItems.exists(EntityType.INTERACTION));
        assertFalse(LegacyItems.exists(EntityType.WARDEN));
    }
}
