package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlaceholderTemplateTest {

    private static List<PlaceholderTemplate.Piece> pieces(String source) {
        return PlaceholderTemplate.parse(source).pieces();
    }

    @Test
    void splitsLiteralsAndPlaceholders() {
        assertEquals(List.of(
                new PlaceholderTemplate.Literal("<gray>Hi "),
                new PlaceholderTemplate.Reference("player_name"),
                new PlaceholderTemplate.Literal(", "),
                new PlaceholderTemplate.Reference("server_online"),
                new PlaceholderTemplate.Literal(" online")
        ), pieces("<gray>Hi %player_name%, %server_online% online"));
    }

    @Test
    void keepsArgumentsWithUnderscores() {
        assertEquals(List.of(new PlaceholderTemplate.Reference("luckperms_meta_chat_color")),
                pieces("%luckperms_meta_chat_color%"));
    }

    @Test
    void notPlaceholders() {
        for (String text : List.of("100%", "50% off, 20%", "%noparams%", "%_x%", "%a_%", "%a b_c%", "%a_b c%", "%%")) {
            assertFalse(PlaceholderTemplate.parse(text).hasReferences(), text);
        }
    }

    @Test
    void detectsPlaceholdersInsideTagArguments() {
        assertEquals(List.of(
                new PlaceholderTemplate.Literal("<click:run_command:'/msg "),
                new PlaceholderTemplate.Reference("player_name", true, "click"),
                new PlaceholderTemplate.Literal("'>"),
                new PlaceholderTemplate.Reference("player_name"),
                new PlaceholderTemplate.Literal("</click>")
        ), pieces("<click:run_command:'/msg %player_name%'>%player_name%</click>"));
    }

    @Test
    void quotedGreaterThanDoesNotEndTag() {
        List<PlaceholderTemplate.Piece> parsed = pieces("<hover:show_text:'a > b %x_y%'>%x_y%");
        assertEquals(new PlaceholderTemplate.Reference("x_y", true, "hover"), parsed.get(1));
        assertEquals(new PlaceholderTemplate.Reference("x_y"), parsed.get(3));
    }

    @Test
    void escapedTagIsNotATag() {
        List<PlaceholderTemplate.Piece> parsed = pieces("\\<not a tag %x_y%");
        assertEquals(new PlaceholderTemplate.Reference("x_y"), parsed.get(1));
    }
}
