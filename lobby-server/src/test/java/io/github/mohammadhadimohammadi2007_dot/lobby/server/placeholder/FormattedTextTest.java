package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormattedTextTest {

    @Test
    void convertsAmpersandAndSectionCodes() {
        assertEquals("<reset><red>Admin", FormattedText.legacyToMiniMessage("&cAdmin"));
        assertEquals("<reset><gold>VIP", FormattedText.legacyToMiniMessage("§6VIP"));
        assertEquals("<bold><reset><red>x", FormattedText.legacyToMiniMessage("&l&cx"));
    }

    @Test
    void convertsHexForms() {
        assertEquals("<reset><#ff8800>x", FormattedText.legacyToMiniMessage("&#ff8800x"));
        assertEquals("<reset><#ff8800>x", FormattedText.legacyToMiniMessage("&x&f&f&8&8&0&0x"));
        assertEquals("<reset><#AbCdEf>x", FormattedText.legacyToMiniMessage("§x§A§b§C§d§E§fx"));
    }

    @Test
    void leavesOtherAmpersandsAlone() {
        assertEquals("Tom & Jerry &z", FormattedText.legacyToMiniMessage("Tom & Jerry &z"));
        assertEquals("&#12zz00", FormattedText.legacyToMiniMessage("&#12zz00"));
    }

    @Test
    void colorCodeEndsBoldLikeMinecraft() {
        Component parsed = FormattedText.parse("&lBold&cRed");
        List<Component> flat = flatten(parsed);
        Component red = flat.stream().filter(part -> text(part).equals("Red")).findFirst().orElseThrow();
        assertEquals(NamedTextColor.RED, red.color());
        assertTrue(red.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE, "red part must not be bold");
    }

    @Test
    void mixedLegacyAndMiniMessage() {
        Component parsed = FormattedText.parse("&6[VIP] <bold>Star");
        assertEquals("[VIP] Star", PlainTextComponentSerializer.plainText().serialize(parsed));
        assertEquals(TextColor.color(NamedTextColor.GOLD), flatten(parsed).stream()
                .filter(part -> text(part).equals("Star")).findFirst().orElseThrow().color());
    }

    /** Every component with its effective style merged from its parents. */
    static List<Component> flatten(Component component) {
        List<Component> out = new ArrayList<>();
        flatten(component, net.kyori.adventure.text.format.Style.empty(), out);
        return out;
    }

    private static void flatten(Component component, net.kyori.adventure.text.format.Style inherited, List<Component> out) {
        net.kyori.adventure.text.format.Style style = inherited.merge(component.style());
        out.add(component.style(style).children(List.of()));
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
