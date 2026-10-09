package io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Which other players someone sees in the lobby. NPCs and holograms are always shown. */
public enum VisibilityMode {
    /** Everybody. */
    ALL,
    /** Only staff (players with the staff permission in hotbar.yml). */
    STAFF,
    /** Nobody. */
    NONE;

    /** The mode the toggle item switches to next: all, then staff, then none, then all again. */
    public VisibilityMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** How it is written in config files and saved: {@code all}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode with this name, or {@code null}. */
    public static @Nullable VisibilityMode fromName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (VisibilityMode mode : values()) {
            if (mode.configName().equalsIgnoreCase(name.strip())) {
                return mode;
            }
        }
        return null;
    }
}
