package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;
import net.minestom.server.color.TeamColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code tab:} and {@code nametags:} sections of {@code display.yml}. They share one config record
 * because they share one team per player: the team decides both the tab order and the nametag.
 *
 * @param header           the lines above the tab list, joined with line breaks
 * @param footer           the lines below it
 * @param nameFormat       how each player is written in the tab list
 * @param sameInstanceOnly true to list only the players of the viewer's own lobby instance
 * @param groupOrder       lower-case group names, first sorted first, for groups without a weight
 * @param nametags         the nametag settings
 */
public record TabConfig(boolean enabled, int intervalTicks, TieredText header, TieredText footer, TieredText nameFormat,
                        boolean sameInstanceOnly, List<String> groupOrder, Nametags nametags) {

    private static final int MAX_INTERVAL_TICKS = 20 * 60;
    private static final int DEFAULT_INTERVAL_TICKS = 20;

    public TabConfig {
        groupOrder = List.copyOf(groupOrder);
    }

    /**
     * Nametags above players' heads.
     *
     * @param nameColor the colour of the name, or {@code null} to take the last colour of the prefix
     */
    public record Nametags(boolean enabled, TieredText prefix, TieredText suffix, @Nullable TeamColor nameColor) {
    }

    static TabConfig read(ConfigReader reader) {
        String show = reader.choice("tab.show", Set.of("server", "instance"));
        String colorName = reader.string("nametags.name-color", "auto").strip();
        TeamColor color = null;
        if (!colorName.equalsIgnoreCase("auto")) {
            color = teamColor(colorName);
            if (color == null) {
                reader.reportInvalid("nametags.name-color", colorName, "auto, or one of Minecraft's 16 colours");
            }
        }
        Nametags nametags = new Nametags(reader.bool("nametags.enabled", true),
                TieredText.of(reader.string("nametags.prefix", ""), optional(reader, "nametags.legacy-prefix")),
                TieredText.of(reader.string("nametags.suffix", ""), optional(reader, "nametags.legacy-suffix")),
                color);
        return new TabConfig(reader.bool("tab.enabled", true),
                reader.integer("tab.update-interval", SidebarConfig.MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS,
                        DEFAULT_INTERVAL_TICKS),
                block(reader, "tab.header", "tab.legacy-header"),
                block(reader, "tab.footer", "tab.legacy-footer"),
                TieredText.of(reader.string("tab.name-format", "%player_name%"),
                        optional(reader, "tab.legacy-name-format")),
                "instance".equals(show),
                reader.stringList("tab.group-order", List.of()).stream()
                        .map(group -> group.strip().toLowerCase(Locale.ROOT)).toList(),
                nametags);
    }

    /** Several lines in the file, one text with line breaks. */
    private static TieredText block(ConfigReader reader, String path, String legacyPath) {
        String modern = String.join("\n", reader.stringList(path, List.of()));
        List<String> legacy = reader.stringList(legacyPath, List.of());
        return TieredText.of(modern, legacy.isEmpty() ? null : String.join("\n", legacy));
    }

    private static @Nullable String optional(ConfigReader reader, String path) {
        String value = reader.string(path, "");
        return value.isEmpty() ? null : value;
    }

    /** One of Minecraft's 16 colours by name ({@code dark_purple}), or {@code null}. */
    static @Nullable TeamColor teamColor(String name) {
        String wanted = name.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TeamColor color : TeamColor.values()) {
            if (color.name().equals(wanted)) {
                return color;
            }
        }
        return null;
    }
}
