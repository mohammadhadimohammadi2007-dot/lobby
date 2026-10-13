package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What players get in the PvP area: items by inventory slot (0-8 the hotbar, 9-35 the rest) and what they
 * wear. Items are kept with all their data (enchantments, names, dye colors...).
 */
public record Kit(Map<Integer, ItemStack> items, Map<EquipmentSlot, ItemStack> equipment) {

    public static final Kit EMPTY = new Kit(Map.of(), Map.of());
    /** Slots of the main inventory: 0-8 hotbar, 9-35 the rest. */
    static final int MAIN_SLOTS = 36;
    /** What a kit can have on: armor and the off hand. */
    static final List<EquipmentSlot> WORN = List.of(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE,
            EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS, EquipmentSlot.OFF_HAND);

    public Kit {
        items = Map.copyOf(items);
        equipment = equipment.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(equipment));
    }

    /** The player's inventory and armor as a kit. Tick thread. */
    public static Kit of(Player player) {
        Map<Integer, ItemStack> items = new TreeMap<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            ItemStack item = inventory.getItemStack(slot);
            if (!item.isAir()) {
                items.put(slot, item);
            }
        }
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WORN) {
            ItemStack item = player.getEquipment(slot);
            if (!item.isAir()) {
                worn.put(slot, item);
            }
        }
        return new Kit(items, worn);
    }

    /** Replaces the player's whole inventory with the kit. Tick thread. */
    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        items.forEach(inventory::setItemStack);
        equipment.forEach(player::setEquipment);
    }

    public boolean isEmpty() {
        return items.isEmpty() && equipment.isEmpty();
    }

    /** Number of items (stacks), for messages. */
    public int size() {
        return items.size() + equipment.size();
    }

    /**
     * The kit as text for data/pvp.yml: slot or equipment name ({@code "0"}, {@code "helmet"}) to the item in
     * SNBT.
     */
    public Map<String, String> encode() {
        Map<String, String> encoded = new LinkedHashMap<>();
        items.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> encoded.put(String.valueOf(entry.getKey()), snbt(entry.getValue())));
        for (EquipmentSlot slot : WORN) {
            ItemStack item = equipment.get(slot);
            if (item != null) {
                encoded.put(slot.name().toLowerCase(Locale.ROOT), snbt(item));
            }
        }
        return encoded;
    }

    /**
     * Reads {@link #encode()}'s text.
     *
     * @throws IllegalArgumentException naming the entry that cannot be read
     */
    public static Kit decode(Map<String, String> encoded) {
        Map<Integer, ItemStack> items = new TreeMap<>();
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            String key = entry.getKey().strip().toLowerCase(Locale.ROOT);
            ItemStack item = item(key, entry.getValue());
            if (key.chars().allMatch(Character::isDigit) && !key.isEmpty()) {
                int slot = Integer.parseInt(key);
                if (slot >= MAIN_SLOTS) {
                    throw new IllegalArgumentException("kit slot " + slot + " does not exist (0-" + (MAIN_SLOTS - 1) + ")");
                }
                items.put(slot, item);
            } else {
                EquipmentSlot slot = WORN.stream().filter(worn1 -> worn1.name().equalsIgnoreCase(key)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("kit entry '" + key + "' is neither a slot number"
                                + " nor helmet, chestplate, leggings, boots or off_hand"));
                worn.put(slot, item);
            }
        }
        return new Kit(items, worn);
    }

    private static String snbt(ItemStack item) {
        try {
            return TagStringIO.tagStringIO().asString(item.toItemNBT());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the item " + item.material().name(), e);
        }
    }

    private static ItemStack item(String key, String snbt) {
        try {
            return ItemStack.fromItemNBT(TagStringIO.tagStringIO().asCompound(snbt));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("kit item '" + key + "' cannot be read: " + e.getMessage(), e);
        }
    }
}