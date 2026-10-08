package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** One menu a player has open: the chest they see and which item sits in each slot for them. */
final class OpenMenu {

    private final MenuDefinition definition;
    private final Inventory inventory;
    private final Map<Integer, MenuItem> itemsBySlot = new HashMap<>();
    private long nextRefreshTick;

    OpenMenu(MenuDefinition definition, Inventory inventory, long tick) {
        this.definition = definition;
        this.inventory = inventory;
        this.nextRefreshTick = definition.refreshTicks() == 0 ? Long.MAX_VALUE : tick + definition.refreshTicks();
    }

    MenuDefinition definition() {
        return definition;
    }

    Inventory inventory() {
        return inventory;
    }

    /** The actions of the item in {@code slot}, or {@code null} if the slot holds nothing clickable. */
    @Nullable ActionList actionsAt(int slot) {
        MenuItem item = itemsBySlot.get(slot);
        return item == null || item.actions().isEmpty() ? null : item.actions();
    }

    /** True if the menu should be rebuilt now. */
    boolean dueForRefresh(long tick) {
        if (tick < nextRefreshTick) {
            return false;
        }
        nextRefreshTick = tick + definition.refreshTicks();
        return true;
    }

    /** True if an item was already placed in {@code slot} by an earlier (higher priority) entry. */
    boolean filled(int slot) {
        return itemsBySlot.containsKey(slot);
    }

    /** Remembers which item ended up in which slot, so clicks know what was clicked. */
    void slotFilled(int slot, MenuItem item) {
        itemsBySlot.put(slot, item);
    }

    void clearSlots() {
        itemsBySlot.clear();
    }

    void setItem(int slot, ItemStack stack) {
        inventory.setItemStack(slot, stack);
    }

    boolean isOpen(Player player) {
        return player.getOpenInventory() == inventory;
    }
}
