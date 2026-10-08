package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** What a hologram shows. */
public enum HologramType {
    /** Lines of text. */
    TEXT,
    /** A floating item. */
    ITEM,
    /** A floating block. */
    BLOCK;

    /** The value written in data/holograms.yml and commands, e.g. {@code text}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** All accepted values. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(HologramType::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** The type with that name, or {@code null}. */
    public static HologramType fromConfigName(String name) {
        for (HologramType type : values()) {
            if (type.configName().equalsIgnoreCase(name.strip())) {
                return type;
            }
        }
        return null;
    }
}
