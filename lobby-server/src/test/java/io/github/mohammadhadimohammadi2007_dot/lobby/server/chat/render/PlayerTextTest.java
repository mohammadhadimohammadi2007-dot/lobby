package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What players type: allowed styling works, everything else stays visible text. */
class PlayerTextTest {

    private static final PlayerTextParser PARSER = new PlayerTextParser(Set.of("color", "bold"));

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void withoutPermissionEverythingIsText() {
        Component parsed = PARSER.parse("<red>hi &cthere", false);
        assertEquals(Component.text("<red>hi &cthere"), parsed);
    }

    @Test
    void allowedStylesWork() {
        Component parsed = PARSER.parse("<red>hi</red> <bold>there", true);
        assertEquals("hi there", plain(parsed));
        assertTrue(parsed.toString().contains("red"));
    }

    @Test
    void legacyCodesOnlyForAllowedStyles() {
        Component colored = PARSER.parse("&cred", true);
        assertEquals("red", plain(colored));
        assertEquals(NamedTextColor.RED, colored.children().isEmpty() ? colored.color() : colored.children().getFirst().color());
        // &o (italic) is not allowed, so it stays as typed.
        assertEquals("&oitalic", plain(PARSER.parse("&oitalic", true)));
    }

    @Test
    void dangerousTagsStayText() {
        String[] attacks = {
                "<click:run_command:/op me>x</click>",
                "<hover:show_text:'<red>boo'>x</hover>",
                "<insert:/op me>x</insert>",
                "<font:uniform>x</font>",
                "<selector:@a>",
                "<nbt:block:'0 0 0':Items>",
                "<key:key.jump>",
                "<lang:block.minecraft.diamond_block>",
                "<obfuscated>x",
                "<italic>x",
                "<newline>",
                "<reset>x",
                "<transition:red:blue:0.5>x",
                "<score:Steve:kills>",
                "<lobby_placeholder_0>",
                "<pride>x",
                "<shadow:red>x",
        };
        for (String attack : attacks) {
            Component parsed = PARSER.parse(attack, true);
            assertEquals(attack, plain(parsed), attack);
            assertNull(parsed.clickEvent(), attack);
            assertNull(parsed.hoverEvent(), attack);
            assertNull(parsed.insertion(), attack);
            assertTrue(parsed.decoration(TextDecoration.OBFUSCATED) != TextDecoration.State.TRUE, attack);
        }
    }

    @Test
    void emojisModernAndLegacy() {
        Map<String, ChatConfig.Emoji> emojis = Map.of("heart", new ChatConfig.Emoji("❤", "<3"));
        assertEquals("i ❤ you", EmojiReplacer.apply("i :heart: you", emojis, false));
        assertEquals("i <3 you", EmojiReplacer.apply("i :heart: you", emojis, true));
        assertEquals("i :unknown: you", EmojiReplacer.apply("i :unknown: you", emojis, false));
    }
}
