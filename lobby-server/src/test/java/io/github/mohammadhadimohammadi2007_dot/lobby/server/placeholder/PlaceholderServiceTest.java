package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rendering with global placeholders (no Minestom server needed), including injection attempts. */
class PlaceholderServiceTest {

    private static final String CLICK_INJECTION = "<click:run_command:'/op attacker'>click me</click>";

    private PlaceholderRegistry registry;
    private PlaceholderService service;
    private String plainValue;
    private String formattedValue;
    private final AtomicInteger computeCount = new AtomicInteger();

    @BeforeEach
    void setUp() {
        registry = new PlaceholderRegistry();
        service = new PlaceholderService(registry);
        registry.register("test", params -> switch (params) {
            case "plain" -> Placeholder.global(Duration.ZERO, () -> plainValue);
            case "formatted" -> new Placeholder.Global(() -> formattedValue, Duration.ZERO, true);
            case "counted" -> Placeholder.global(Duration.ofHours(1), () -> String.valueOf(computeCount.incrementAndGet()));
            default -> null;
        });
    }

    private Component render(String template) {
        return service.render(template, null);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void replacesGlobalPlaceholder() {
        plainValue = "42";
        assertEquals("Online: 42", plain(render("<gray>Online: %test_plain%")));
    }

    @Test
    void unknownPlaceholdersStayAsWritten() {
        assertEquals("%nope_thing% and %test_missing%", plain(render("%nope_thing% and %test_missing%")));
    }

    @Test
    void playerPlaceholderWithoutPlayerStaysAsWritten() {
        registry.register("p", params -> Placeholder.player(player -> "x"));
        assertEquals("%p_name%", plain(render("%p_name%")));
    }

    @Test
    void plainValueCannotInjectMiniMessage() {
        plainValue = CLICK_INJECTION;
        Component result = render("<gold>%test_plain%</gold>");

        assertEquals(CLICK_INJECTION, plain(result));
        assertTrue(events(result).isEmpty(), "no click or hover may appear: " + events(result));
    }

    @Test
    void plainValueCannotInjectLegacyColors() {
        plainValue = "&4&lFAKE ADMIN";
        Component result = render("%test_plain%");

        assertEquals("&4&lFAKE ADMIN", plain(result));
        assertTrue(FormattedTextTest.flatten(result).stream().noneMatch(part -> NamedTextColor.DARK_RED.equals(part.color())));
    }

    @Test
    void formattedValueCannotCloseOuterTags() {
        formattedValue = "</gold></hover><red>x";
        Component result = render("<gold>%test_formatted% after</gold>");

        Component after = FormattedTextTest.flatten(result).stream()
                .filter(part -> plain(part).equals(" after")).findFirst().orElseThrow();
        assertEquals(NamedTextColor.GOLD, after.color(), "text after the value must keep its own color");
    }

    @Test
    void formattedValueShowsColors() {
        formattedValue = "&6[VIP]";
        Component result = render("%test_formatted% Steve");

        assertEquals("[VIP] Steve", plain(result));
        assertTrue(FormattedTextTest.flatten(result).stream().anyMatch(part -> NamedTextColor.GOLD.equals(part.color())));
    }

    @Test
    void valueInsideClickArgumentIsSanitized() {
        plainValue = "x' <red>:evil\\";
        Component result = render("<click:run_command:'/msg %test_plain%'>go</click>");

        ClickEvent click = events(result).stream().map(Component::clickEvent).filter(e -> e != null).findFirst().orElseThrow();
        assertEquals("/msg x redevil", ((ClickEvent.Payload.Text) click.payload()).value());
    }

    @Test
    void valueInsideHoverIsInsertedSafely() {
        plainValue = "<bold>hi";
        formattedValue = "&6gold";
        Component result = render("<hover:show_text:'Value: %test_plain% %test_formatted%'>x</hover>");

        HoverEvent<?> hover = events(result).stream().map(Component::hoverEvent).filter(e -> e != null).findFirst().orElseThrow();
        Component shown = (Component) hover.value();
        assertEquals("Value: <bold>hi gold", plain(shown), "plain value shown exactly as written");
        assertTrue(FormattedTextTest.flatten(shown).stream().anyMatch(part -> NamedTextColor.GOLD.equals(part.color())),
                "formatted value keeps its color inside hover text");
    }

    @Test
    void globalValuesAreCached() {
        render("%test_counted% %test_counted%");
        render("%test_counted%");

        assertEquals(1, computeCount.get(), "computed once, then served from the cache");
        service.invalidateAll();
        render("%test_counted%");
        assertEquals(2, computeCount.get());
    }

    @Test
    void extraTagsStillWork() {
        plainValue = "1";
        Component result = service.render("<prefix>%test_plain%", null,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("prefix", "[x] "));
        assertEquals("[x] 1", plain(result));
    }

    @Test
    void registryRejectsBadNamesAndDuplicates() {
        try {
            registry.register("test", params -> null);
            throw new AssertionError("duplicate must fail");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            registry.register("bad_name", params -> null);
            throw new AssertionError("underscore must fail");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        assertNull(registry.find("nothing"));
    }

    /** All components that carry a click or hover event. */
    private static List<Component> events(Component component) {
        return FormattedTextTest.flatten(component).stream()
                .filter(part -> part.clickEvent() != null || part.hoverEvent() != null)
                .toList();
    }
}
