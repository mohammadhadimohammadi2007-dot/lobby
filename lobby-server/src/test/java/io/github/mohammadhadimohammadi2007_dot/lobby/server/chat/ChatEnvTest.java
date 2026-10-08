package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatTestServer.Joined;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.RenderedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sends chat through the real pipeline with fake players and checks what each player receives. */
@EnvTest
class ChatEnvTest {

    @TempDir
    Path dir;

    private ChatTestServer start(Env env) throws Exception {
        return ChatTestServer.start(env, dir, UnaryOperator.identity());
    }

    @Test
    void localMessageReachesEveryone(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");

        server.say(steve, "hello there");

        assertEquals(List.of("Steve: hello there"), steve.lines());
        assertEquals(List.of("Steve: hello there"), alex.lines());
    }

    @Test
    void newPlayerMustMoveFirst(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");
        server.services().players().joined(steve.player().getUuid()); // As if just joined, not moved yet.

        server.say(steve, "hi");

        assertEquals(List.of("Move a little before you start chatting."), steve.lines());
        assertEquals(List.of(), alex.lines());
    }

    @Test
    void blockedWordIsNotDelivered(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");

        server.say(steve, "you f.u.c.k.e.r");

        assertTrue(steve.lines().stream().anyMatch(line -> line.startsWith("Your message was not sent")), steve.lines()::toString);
        assertEquals(List.of(), alex.lines());
    }

    @Test
    void playerTagsAndPlaceholdersAreShownAsText(Env env) throws Exception {
        ChatTestServer server = start(env);
        // Admin first: operators have every permission, so others would see Admin's VIP join announcement.
        Joined admin = server.join("Admin"); // Operator: may use the allowed styling tags.
        Joined steve = server.join("Steve");
        String attack = "<click:run_command:/op Steve>free</click> <hover:show_text:'x'>h</hover> %server_online% <lobby_placeholder_0>";

        server.say(steve, attack);
        server.say(admin, attack);

        List<Component> received = steve.components();
        assertEquals(2, received.size());
        assertEquals("Steve: " + attack, plain(received.getFirst()));
        for (Component message : received) {
            assertFalse(hasRunCommand(message), "a player made a clickable command");
            assertTrue(plain(message).endsWith(attack), "player text must stay as typed: " + plain(message));
        }
    }

    @Test
    void staffChannelOnlyReachesStaff(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined admin = server.join("Admin");
        Joined steve = server.join("Steve");

        server.say(admin, "#secret plan");

        assertEquals(List.of(), steve.lines());
        assertEquals(List.of("[Staff] Admin » secret plan"), admin.lines());
    }

    @Test
    void ignoredPlayerIsHidden(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");
        server.services().settings().update(alex.player().getUuid(),
                settings -> settings.withIgnored(steve.player().getUuid(), true));

        server.say(steve, "can you hear me");

        assertEquals(List.of(), alex.lines());
        assertEquals(List.of("Steve: can you hear me"), steve.lines());
    }

    @Test
    void cooldownStopsBursts(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");

        server.say(steve, "one");
        server.say(steve, "two");
        server.say(steve, "three");
        server.say(steve, "four");

        List<String> lines = steve.lines();
        assertEquals(4, lines.size(), lines::toString);
        assertTrue(lines.get(3).startsWith("Slow down!"), lines::toString);
    }

    @Test
    void emojisAreReplaced(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");

        server.say(steve, "i :heart: this");

        assertEquals(List.of("Steve: i ❤ this"), steve.lines());
    }

    @Test
    void sameVariantIsRenderedOnce(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        for (int i = 0; i < 6; i++) {
            server.join("Viewer" + i);
        }

        server.say(steve, "render me once");

        AtomicReference<RenderedMessage> rendered = new AtomicReference<>();
        server.chat.service().onChatThread(() -> rendered.set(server.services().history().messages().getLast().rendered()))
                .get(5, TimeUnit.SECONDS);
        assertEquals(7, rendered.get().recipients());
        assertEquals(1, rendered.get().renderCount());
    }

    @Test
    void mentionHighlightsForTheMentionedPlayerOnly(Env env) throws Exception {
        ChatTestServer server = start(env);
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");
        Joined bob = server.join("Bob");

        server.say(steve, "hey Alex look");

        Component forAlex = alex.components().getFirst();
        Component forBob = bob.components().getFirst();
        assertEquals(plain(forBob), plain(forAlex));
        assertFalse(forAlex.equals(forBob), "the mentioned player should see a highlighted version");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static boolean hasRunCommand(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) {
            return true;
        }
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.value() instanceof Component text && hasRunCommand(text)) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasRunCommand(child)) {
                return true;
            }
        }
        return false;
    }
}
