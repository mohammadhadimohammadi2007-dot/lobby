package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** One menu a player has open: the chest they see and which item sits in each slot for them. */
final class OpenMenu {

    private final MenuDefinition definition;
    private final Inventory inventory;
    private volatile Map<Integer, MenuItem> itemsBySlot = Map.of();
    private final AtomicBoolean building = new AtomicBoolean();
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

    /** True if no rebuild is on its way; the caller then owns the next one until {@link #built()}. */
    boolean startBuilding() {
        return building.compareAndSet(false, true);
    }

    /** The rebuild started with {@link #startBuilding()} is done (shown or dropped). */
    void built() {
        building.set(false);
    }

    /**
     * Puts the contents in the chest and remembers which item is in which slot, so clicks know what was
     * clicked. Only slots whose item changed are sent again. Tick thread (or before the menu is opened).
     */
    void show(MenuContents contents) {
        ItemStack[] items = contents.items();
        for (int slot = 0; slot < items.length; slot++) {
            if (!inventory.getItemStack(slot).equals(items[slot])) {
                inventory.setItemStack(slot, items[slot]);
            }
        }
        itemsBySlot = contents.itemsBySlot();
    }

    boolean isOpen(Player player) {
        return player.getOpenInventory() == inventory;
    }
}
