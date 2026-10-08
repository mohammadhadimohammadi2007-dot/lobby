package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One menu from menus.yml.
 *
 * @param rows           1-6 rows of nine slots
 * @param refreshTicks   how often the items are rebuilt while open (0 = never)
 * @param fill           material put in every empty slot, or {@code null} for none
 * @param items          the items, in file order
 */
public record MenuDefinition(String name, String title, int rows, int refreshTicks, @Nullable Material fill,
                             List<MenuItem> items) {

    public static final int SLOTS_PER_ROW = 9;
    public static final int MAX_ROWS = 6;

    public MenuDefinition {
        items = List.copyOf(items);
    }

    public int size() {
        return rows * SLOTS_PER_ROW;
    }

    /** The chest size Minecraft needs for these rows. */
    public InventoryType inventoryType() {
        return switch (rows) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            default -> InventoryType.CHEST_6_ROW;
        };
    }
}
