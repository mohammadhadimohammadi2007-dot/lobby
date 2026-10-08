package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a hologram turns towards the player. The numbers are Minecraft's own, sent in the display
 * entity's metadata. Old clients see armor stands, whose text always faces the player, so this only
 * changes what modern clients see.
 */
public enum Billboard {
    /** Never turns; shows its back when seen from behind. */
    FIXED(0),
    /** Turns around the up axis only, like a signboard. */
    VERTICAL(1),
    /** Turns around the horizontal axis only. */
    HORIZONTAL(2),
    /** Always faces the player (the default, and what old clients always do). */
    CENTER(3);

    private final byte value;

    Billboard(int value) {
        this.value = (byte) value;
    }

    /** The value Minecraft expects in the metadata. */
    public byte value() {
        return value;
    }

    /** The value written in data/holograms.yml, e.g. {@code center}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** All accepted values. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(Billboard::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** The billboard with that name, or {@code null}. */
    public static Billboard fromConfigName(String name) {
        for (Billboard billboard : values()) {
            if (billboard.configName().equalsIgnoreCase(name.strip())) {
                return billboard;
            }
        }
        return null;
    }
}
