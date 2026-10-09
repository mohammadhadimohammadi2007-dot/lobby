package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Action lists as they are written in data files: each entry is either a line such as
 * {@code "message: Hello"} or a section such as {@code random:} holding a list of its own.
 *
 * <p>Data files keep the entries exactly like that, instead of as text, so a {@code random:} section an
 * admin wrote by hand survives every save unchanged.
 */
public final class ActionEntries {

    private ActionEntries() {
    }

    /** Writes the entries as a YAML list under {@code node}, sections as sections. */
    public static void write(List<?> entries, ConfigurationNode node) throws SerializationException {
        node.raw(null);
        for (Object entry : entries) {
            node.appendListNode().raw(entry);
        }
    }

    /** One entry in one line, for messages: {@code random: [message: a, message: b]}. */
    public static String describe(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(item -> item.getKey() + ": " + describeValue(item.getValue()))
                    .collect(Collectors.joining(", "));
        }
        return String.valueOf(entry);
    }

    /** All entries in one line, separated by {@code |}, or {@code none}. */
    public static String describeAll(List<?> entries) {
        if (entries.isEmpty()) {
            return "none";
        }
        return entries.stream().map(ActionEntries::describe).collect(Collectors.joining(" | "));
    }

    private static String describeValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(ActionEntries::describe).collect(Collectors.joining(", ", "[", "]"));
        }
        return describe(value);
    }
}
