package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Who a hologram is shown to. The same three modes FancyHolograms has. */
public enum HologramVisibility {

    /** Everyone sees it (unless a permission is set as well). */
    ALL,
    /** Only players with the hologram's permission see it. */
    PERMISSION,
    /**
     * Nobody sees it until someone is shown it with {@code /hologram show}. The list is not saved, so
     * it starts empty after a restart, exactly like FancyHolograms' manual mode.
     */
    MANUAL;

    /** The value written in data/holograms.yml, e.g. {@code permission}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** All accepted values. */
    public static Set<String> configNames() {
        return Arrays.stream(values()).map(HologramVisibility::configName).collect(Collectors.toUnmodifiableSet());
    }

    /** The mode with that name, or {@code null}. */
    public static HologramVisibility fromConfigName(String name) {
        for (HologramVisibility visibility : values()) {
            if (visibility.configName().equalsIgnoreCase(name.strip())) {
                return visibility;
            }
        }
        return null;
    }
}
