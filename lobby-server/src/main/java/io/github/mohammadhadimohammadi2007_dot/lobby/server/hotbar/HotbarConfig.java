package io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItem;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything in {@code hotbar.yml}: the items players get in their hotbar, and the player visibility
 * settings. An item that cannot be read is reported by name and left out.
 *
 * @param clearInventory true to empty the inventory before the items are given
 */
public record HotbarConfig(boolean enabled, boolean clearInventory, List<Item> items, Visibility visibility) {

    public static final int HOTBAR_SLOTS = 9;
    private static final int MAX_AMOUNT = 64;
    private static final int MAX_COOLDOWN_MILLIS = 60_000;
    private static final int DEFAULT_COOLDOWN_MILLIS = 250;

    public HotbarConfig {
        items = List.copyOf(items);
    }

    /**
     * One hotbar item.
     *
     * @param id          its name in the file
     * @param slot        0-8, left to right
     * @param permission  needed to get it, {@code ""} for everyone
     * @param look        how it looks (name, lore, glow), with {@code slots} holding {@link #slot}
     * @param actions     what right-clicking with it does
     * @param modeLooks   for the visibility toggle: its look in each mode; empty for a normal item
     */
    public record Item(String id, int slot, String permission, MenuItem look, ActionList actions,
                       Map<VisibilityMode, MenuItem> modeLooks) {

        public Item {
            modeLooks = Map.copyOf(modeLooks);
        }

        /** True for the item that switches who you see. */
        public boolean visibilityToggle() {
            return !modeLooks.isEmpty();
        }

        /** How it looks in this visibility mode (its normal look for a normal item). */
        public MenuItem look(VisibilityMode mode) {
            return modeLooks.getOrDefault(mode, look);
        }
    }

    /**
     * Player visibility.
     *
     * @param defaultMode     what a player who never chose sees
     * @param staffPermission who counts as staff for {@link VisibilityMode#STAFF}
     * @param cooldownMillis  the shortest time between two switches
     */
    public record Visibility(VisibilityMode defaultMode, String staffPermission, long cooldownMillis) {
    }

    /** Reads hotbar.yml. */
    public static HotbarConfig read(ConfigReader reader) {
        List<Item> items = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (String id : reader.keys("items")) {
            Item item = item(reader, id);
            if (item == null) {
                continue;
            }
            if (!used.add(item.slot())) {
                reader.reportInvalid("items." + id + ".slot", item.slot(), "a slot no other item uses");
                continue;
            }
            items.add(item);
        }
        String modeName = reader.string("visibility.default", "all");
        VisibilityMode mode = VisibilityMode.fromName(modeName);
        if (mode == null) {
            reader.reportInvalid("visibility.default", modeName, "all, staff or none");
            mode = VisibilityMode.ALL;
        }
        Visibility visibility = new Visibility(mode,
                reader.string("visibility.staff-permission", "lobby.visibility.staff").strip(),
                reader.integer("visibility.cooldown", 0, MAX_COOLDOWN_MILLIS, 3000));
        return new HotbarConfig(reader.bool("enabled", true), reader.bool("clear-inventory", true), items, visibility);
    }

    private static @Nullable Item item(ConfigReader reader, String id) {
        String path = "items." + id;
        int slot = reader.integer(path + ".slot", 0, HOTBAR_SLOTS - 1, -1);
        if (slot < 0) {
            reader.reportInvalid(path + ".slot", "nothing", "a hotbar slot from 0 (left) to 8 (right)");
            return null;
        }
        boolean toggle = "visibility".equalsIgnoreCase(reader.string(path + ".type", "item").strip());
        Map<VisibilityMode, MenuItem> modeLooks = new EnumMap<>(VisibilityMode.class);
        if (toggle) {
            for (VisibilityMode mode : VisibilityMode.values()) {
                MenuItem look = look(reader, path + ".modes." + mode.configName(), slot);
                if (look == null) {
                    return null;
                }
                modeLooks.put(mode, look);
            }
        }
        MenuItem look = toggle ? modeLooks.get(VisibilityMode.ALL) : look(reader, path, slot);
        if (look == null) {
            return null;
        }
        int cooldown = reader.integer(path + ".click-cooldown", 0, MAX_COOLDOWN_MILLIS, DEFAULT_COOLDOWN_MILLIS);
        String where = reader.fileName() + ": " + path;
        ActionList actions = ActionParser.parseList(reader.entryList(path + ".actions"), cooldown, where + ".actions",
                reader::addWarning);
        return new Item(id.toLowerCase(Locale.ROOT), slot, reader.string(path + ".permission", "").strip(), look,
                actions, modeLooks);
    }

    /** {@code material}, {@code amount}, {@code name}, {@code lore} and {@code glow} at {@code path}. */
    private static @Nullable MenuItem look(ConfigReader reader, String path, int slot) {
        Material material = MenuConfig.material(reader, path + ".material", reader.string(path + ".material", "stone"));
        if (material == null) {
            return null;
        }
        return new MenuItem(List.of(slot), material, reader.integer(path + ".amount", 1, MAX_AMOUNT, 1),
                reader.string(path + ".name", ""), reader.stringList(path + ".lore", List.of()),
                reader.bool(path + ".glow", false), List.of(), ActionList.EMPTY);
    }
}
