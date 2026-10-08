package io.github.mohammadhadimohammadi2007_dot.lobby.server.data;

import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for reading data files, the same way {@code ConfigReader} reads config files. */
public final class DataNodes {

    private DataNodes() {
    }

    /**
     * A list of whole entries: text, or a section such as the {@code random:} of an action list. Used
     * for the {@code actions} of a hologram, an NPC or a portal.
     */
    public static List<Object> entryList(ConfigurationNode node) {
        if (!node.isList()) {
            return List.of();
        }
        List<Object> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            if (child.isMap()) {
                Map<String, Object> map = new LinkedHashMap<>();
                child.childrenMap().forEach((key, value) -> map.put(String.valueOf(key),
                        value.isList() ? rawList(value) : value.raw()));
                values.add(map);
            } else if (child.raw() != null && !String.valueOf(child.raw()).isBlank()) {
                values.add(String.valueOf(child.raw()).strip());
            }
        }
        return values;
    }

    /** The plain strings of a list node, for the lines of a hologram or the lore of an item. */
    public static List<String> stringList(ConfigurationNode node) {
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            values.add(child.raw() == null ? "" : String.valueOf(child.raw()));
        }
        return values;
    }

    private static List<Object> rawList(ConfigurationNode node) {
        List<Object> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            values.add(child.isMap() ? entryList(child) : child.raw());
        }
        return values;
    }
}
