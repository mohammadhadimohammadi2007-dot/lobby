package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what a player typed into a component.
 *
 * <p>Without styling permission the text is shown exactly as typed. With it, only the styling tags allowed in
 * chat.yml work (colors, bold...). Every other tag, including click, hover, insertion, font, selector, nbt,
 * keybind and translatable, is shown as plain text, because the parser simply does not know it.
 */
public final class PlayerTextParser {

    private static final Map<Character, String> LEGACY_COLORS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"));
    private static final Map<Character, String> LEGACY_DECORATIONS = Map.of(
            'l', "bold", 'o', "italic", 'n', "underlined", 'm', "strikethrough", 'k', "obfuscated");

    private final Set<String> allowed;
    private final MiniMessage restricted;

    /** @param allowedTags names from chat.yml's player-formatting (already limited to safe ones) */
    public PlayerTextParser(Set<String> allowedTags) {
        this.allowed = Set.copyOf(allowedTags);
        List<TagResolver> resolvers = new ArrayList<>();
        if (allowed.contains("color")) {
            resolvers.add(StandardTags.color());
        }
        for (TextDecoration decoration : TextDecoration.values()) {
            String name = decoration.name().toLowerCase(java.util.Locale.ROOT);
            if (allowed.contains(name)) {
                resolvers.add(StandardTags.decorations(decoration));
            }
        }
        if (allowed.contains("gradient")) {
            resolvers.add(StandardTags.gradient());
        }
        if (allowed.contains("rainbow")) {
            resolvers.add(StandardTags.rainbow());
        }
        this.restricted = MiniMessage.builder()
                .tags(TagResolver.resolver(resolvers))
                .strict(false)
                .build();
    }

    /**
     * @param text      what the player typed (possibly censored)
     * @param formatted true if the player has the styling permission
     */
    public Component parse(String text, boolean formatted) {
        if (!formatted || allowed.isEmpty() || (text.indexOf('<') < 0 && text.indexOf('&') < 0)) {
            return Component.text(text);
        }
        return restricted.deserialize(legacyCodes(text));
    }

    /** Turns {@code &c}-style codes into tags, but only for styles that are allowed. */
    private String legacyCodes(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                String color = LEGACY_COLORS.get(code);
                String decoration = LEGACY_DECORATIONS.get(code);
                if (color != null && allowed.contains("color")) {
                    out.append('<').append(color).append('>');
                    i++;
                    continue;
                }
                if (decoration != null && allowed.contains(decoration)) {
                    out.append('<').append(decoration).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
