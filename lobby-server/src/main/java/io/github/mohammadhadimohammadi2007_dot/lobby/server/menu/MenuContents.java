package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import net.minestom.server.item.ItemStack;

import java.util.Map;

/**
 * What one player's menu shows: the item in every slot, and which menu item each one came from (for its
 * click actions). Built away from the tick thread, then shown on it.
 *
 * @param items       one per slot, air for empty ones
 * @param itemsBySlot the menu item in each filled slot
 */
record MenuContents(ItemStack[] items, Map<Integer, MenuItem> itemsBySlot) {
}
