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
