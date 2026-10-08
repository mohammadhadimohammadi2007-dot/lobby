package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.compat.LegacyItems;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything in {@code menus.yml}, already checked. A menu or item that cannot be read is reported with its
 * name and skipped, so one mistake never empties the whole file.
 */
public record MenuConfig(Map<String, MenuDefinition> menus) {

    private static final int MAX_AMOUNT = 64;
    private static final int MAX_REFRESH_TICKS = 20 * 60;
    private static final int MAX_CLICK_COOLDOWN_MILLIS = 60_000;
    private static final int DEFAULT_CLICK_COOLDOWN_MILLIS = 250;

    public MenuConfig {
        menus = Map.copyOf(menus);
    }

    public @Nullable MenuDefinition menu(String name) {
        return menus.get(name.toLowerCase(Locale.ROOT));
    }

    /** Reads menus.yml. */
    public static MenuConfig read(ConfigReader reader) {
        Map<String, MenuDefinition> menus = new LinkedHashMap<>();
        for (String name : reader.keys("menus")) {
            String path = "menus." + name;
            int rows = reader.integer(path + ".rows", 1, MenuDefinition.MAX_ROWS, 3);
            String fillName = reader.string(path + ".fill", "").strip();
            Material fill = fillName.isEmpty() ? null : material(reader, path + ".fill", fillName);
            List<MenuItem> items = new ArrayList<>();
            for (String itemName : reader.keys(path + ".items")) {
                MenuItem item = readItem(reader, path + ".items." + itemName, rows);
                if (item != null) {
                    items.add(item);
                }
            }
            menus.put(name.toLowerCase(Locale.ROOT), new MenuDefinition(name.toLowerCase(Locale.ROOT),
                    reader.string(path + ".title", "<dark_gray>" + name),
                    rows, reader.integer(path + ".refresh", 0, MAX_REFRESH_TICKS, 0), fill, items));
        }
        return new MenuConfig(menus);
    }

    private static @Nullable MenuItem readItem(ConfigReader reader, String path, int rows) {
        List<Integer> slots = slots(reader, path, rows);
        if (slots.isEmpty()) {
            return null;
        }
        String materialName = reader.string(path + ".material", "stone");
        Material material = material(reader, path + ".material", materialName);
        if (material == null) {
            return null;
        }
        List<MenuCondition> conditions = new ArrayList<>();
        for (String line : reader.stringList(path + ".show-if", List.of())) {
            try {
                conditions.add(MenuCondition.parse(line));
            } catch (MenuCondition.ConditionException e) {
                reader.reportInvalid(path + ".show-if", line, e.getMessage());
            }
        }
        ActionList actions = ActionParser.parseList(reader.entryList(path + ".actions"),
                reader.integer(path + ".click-cooldown", 0, MAX_CLICK_COOLDOWN_MILLIS, DEFAULT_CLICK_COOLDOWN_MILLIS),
                reader.fileName() + ": " + path + ".actions", reader::addWarning);
        return new MenuItem(slots, material, reader.integer(path + ".amount", 1, MAX_AMOUNT, 1),
                reader.string(path + ".name", ""), reader.stringList(path + ".lore", List.of()),
                reader.bool(path + ".glow", false), conditions, actions);
    }

    /** The slots of one item: {@code slot: 4} or {@code slots: [0, 8]}, or row and column. */
    private static List<Integer> slots(ConfigReader reader, String path, int rows) {
        int size = rows * MenuDefinition.SLOTS_PER_ROW;
        List<Integer> slots = new ArrayList<>();
        for (String value : reader.stringList(path + ".slots", List.of())) {
            Integer slot = slot(reader, path + ".slots", value, size);
            if (slot != null) {
                slots.add(slot);
            }
        }
        String single = reader.string(path + ".slot", "").strip();
        if (!single.isEmpty()) {
            Integer slot = slot(reader, path + ".slot", single, size);
            if (slot != null) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            reader.reportInvalid(path, "no slot", "a slot between 0 and " + (size - 1)
                    + " in 'slot', or several in 'slots'");
        }
        return slots;
    }

    private static @Nullable Integer slot(ConfigReader reader, String option, String value, int size) {
        try {
            int slot = Integer.parseInt(value.strip());
            if (slot < 0 || slot >= size) {
                reader.reportInvalid(option, value, "a slot between 0 and " + (size - 1) + " for this many rows");
                return null;
            }
            return slot;
        } catch (NumberFormatException e) {
            reader.reportInvalid(option, value, "a slot number");
            return null;
        }
    }

    /** The material, or {@code null} with a warning if the name is unknown. */
    private static @Nullable Material material(ConfigReader reader, String option, String name) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        Material material = Material.fromKey(key.contains(":") ? key : "minecraft:" + key);
        if (material == null) {
            reader.reportInvalid(option, name, "an item name like \"diamond_sword\"");
            return null;
        }
        LegacyItems.warnIfMissing(material, reader.fileName() + ": " + option);
        return material;
    }
}
