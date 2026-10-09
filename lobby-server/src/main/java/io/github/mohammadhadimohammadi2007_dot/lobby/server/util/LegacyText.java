package io.github.mohammadhadimohammadi2007_dot.lobby.server.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Text for old clients, which only know the 16 colors and count {@code §} color codes as characters.
 * Used where 1.8-1.12 clients have hard length limits (team prefixes, sidebar lines).
 */
public final class LegacyText {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .build();

    private LegacyText() {
    }

    /** The text as an old client sees it, colors written as {@code §} codes. */
    public static String serialize(Component component) {
        return SERIALIZER.serialize(component);
    }

    public static Component deserialize(String legacy) {
        return SERIALIZER.deserialize(legacy);
    }

    /** Length of the text in characters, color codes included, as old clients count it. */
    public static int length(Component component) {
        return serialize(component).length();
    }

    /**
     * Shortens {@code component} to at most {@code maxChars} legacy characters (color codes included),
     * never leaving half a color code at the end. Text that fits is returned unchanged.
     */
    public static Component limit(Component component, int maxChars) {
        String legacy = serialize(component);
        if (legacy.length() <= maxChars) {
            return component;
        }
        return deserialize(cut(legacy, maxChars));
    }

    /**
     * A line split over two parts of at most {@code maxChars} each, as old clients need for a sidebar
     * line (team prefix, then suffix). The second part starts with the colour and styles the first one
     * ended with, so the line looks unbroken; whatever does not fit in both is cut off.
     *
     * @param first  the text for the prefix
     * @param second the text for the suffix, empty if the line fits in the prefix
     */
    public record Split(Component first, Component second) {
    }

    public static Split split(Component component, int maxChars) {
        String legacy = serialize(component);
        if (legacy.length() <= maxChars) {
            return new Split(component, Component.empty());
        }
        String first = cut(legacy, maxChars);
        String rest = legacy.substring(first.length());
        String carried = rest.startsWith(String.valueOf(LegacyComponentSerializer.SECTION_CHAR))
                ? rest : activeCodes(first) + rest;
        return new Split(deserialize(first), deserialize(cut(carried, maxChars)));
    }

    /** The colour code and the style codes after it that are in effect at the end of {@code legacy}. */
    static String activeCodes(String legacy) {
        String color = "";
        StringBuilder styles = new StringBuilder();
        for (int i = 0; i + 1 < legacy.length(); i++) {
            if (legacy.charAt(i) != LegacyComponentSerializer.SECTION_CHAR) {
                continue;
            }
            char code = Character.toLowerCase(legacy.charAt(i + 1));
            if (Character.digit(code, 16) >= 0 || code == 'r') {
                // A colour (or reset) ends every style, as in Minecraft.
                color = code == 'r' ? "" : legacy.substring(i, i + 2);
                styles.setLength(0);
            } else if (code >= 'k' && code <= 'o') {
                styles.append(legacy, i, i + 2);
            }
            i++;
        }
        return color + styles;
    }

    /** Cuts a legacy string to {@code maxChars} without ending on a lone {@code §}. */
    public static String cut(String legacy, int maxChars) {
        if (legacy.length() <= maxChars) {
            return legacy;
        }
        String cut = legacy.substring(0, maxChars);
        // Drop trailing color codes and a dangling section sign; they would show nothing anyway.
        while (!cut.isEmpty() && (cut.charAt(cut.length() - 1) == LegacyComponentSerializer.SECTION_CHAR
                || (cut.length() >= 2 && cut.charAt(cut.length() - 2) == LegacyComponentSerializer.SECTION_CHAR))) {
            cut = cut.substring(0, cut.length() - (cut.charAt(cut.length() - 1) == LegacyComponentSerializer.SECTION_CHAR ? 1 : 2));
        }
        return cut;
    }
}
