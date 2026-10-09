package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;

/**
 * Everything in {@code display.yml}: the scoreboard, tab list, nametags, boss bar, join title and action
 * bar. Read with the other config files and replaced as a whole by {@code /lobby reload}.
 */
public record DisplayConfig(SidebarConfig sidebar, TabConfig tab, BarsConfig bars) {

    /** Reads display.yml; mistakes fall back to the defaults with a warning, like every other file. */
    public static DisplayConfig read(ConfigReader reader) {
        return new DisplayConfig(SidebarConfig.read(reader), TabConfig.read(reader), BarsConfig.read(reader));
    }
}
