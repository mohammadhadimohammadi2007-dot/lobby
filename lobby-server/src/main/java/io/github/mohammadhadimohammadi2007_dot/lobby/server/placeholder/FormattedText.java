package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

/**
 * Turns trusted text (config files, LuckPerms prefixes) into components. Accepts MiniMessage
 * ({@code <red>}), legacy codes ({@code &c}, {@code §c}), legacy hex ({@code &#ff8800}) and Bukkit's
 * long hex form ({@code &x&f&f&8&8&0&0}), also mixed in one string.
 *
 * <p>Never use this for player-written text: MiniMessage tags such as click and hover would work.
 */
public final class FormattedText {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int HEX_DIGITS = 6;
    /** Length of {@code &x&r&r&g&g&b&b}. */
    private static final int LONG_HEX_LENGTH = 14;

    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"), Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"), Map.entry('o', "italic"));

    private FormattedText() {
    }

    /** Parses trusted text with colors into a component. */
    public static Component parse(String text) {
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(text));
    }

    /**
     * Rewrites legacy color codes as MiniMessage tags. Like in Minecraft, a color code also ends bold,
     * italic and the other decorations, so it becomes {@code <reset><color>}.
     */
    public static String legacyToMiniMessage(String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                String longHex = longHex(text, i);
                if (longHex != null) {
                    out.append("<reset><#").append(longHex).append('>');
                    i += LONG_HEX_LENGTH;
                    continue;
                }
                if (code == '#' && isHex(text, i + 2)) {
                    out.append("<reset><#").append(text, i + 2, i + 2 + HEX_DIGITS).append('>');
                    i += 2 + HEX_DIGITS;
                    continue;
                }
                if (code == 'r') {
                    out.append("<reset>");
                    i += 2;
                    continue;
                }
                String name = CODES.get(code);
                if (name != null) {
                    boolean color = Character.isDigit(code) || (code >= 'a' && code <= 'f');
                    out.append(color ? "<reset><" : "<").append(name).append('>');
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isHex(String text, int start) {
        if (start + HEX_DIGITS > text.length()) {
            return false;
        }
        for (int i = start; i < start + HEX_DIGITS; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Reads {@code &x&r&r&g&g&b&b} starting at {@code start}, or returns {@code null}. */
    private static String longHex(String text, int start) {
        if (start + LONG_HEX_LENGTH > text.length() || Character.toLowerCase(text.charAt(start + 1)) != 'x') {
            return null;
        }
        StringBuilder hex = new StringBuilder(HEX_DIGITS);
        for (int i = start + 2; i < start + LONG_HEX_LENGTH; i += 2) {
            char marker = text.charAt(i);
            char digit = text.charAt(i + 1);
            if ((marker != '&' && marker != '§') || Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }
}
