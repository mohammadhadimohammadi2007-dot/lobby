package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How lines of different lengths line up. Only modern clients can show this; old clients see one
 * armor stand per line, which is always centred.
 */
public enum TextAlignment {
    CENTER(0),
    LEFT(1),
    RIGHT(2);

    /** Where the alignment sits inside the text display's flags byte. */
    static final int FLAG_SHIFT = 3;

    private final int bits;

    TextAlignment(int bits) {
        this.bits = bits;
    }

    /** The bits this alignment contributes to the flags byte. */
    public int bits() {
        return bits << FLAG_SHIFT;
    }

    /** The value written in data/holograms.yml, e.g. {@code left}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** All accepted values. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(TextAlignment::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** The alignment with that name, or {@code null}. */
    public static TextAlignment fromConfigName(String name) {
        for (TextAlignment alignment : values()) {
            if (alignment.configName().equalsIgnoreCase(name.strip())) {
                return alignment;
            }
        }
        return null;
    }
}
