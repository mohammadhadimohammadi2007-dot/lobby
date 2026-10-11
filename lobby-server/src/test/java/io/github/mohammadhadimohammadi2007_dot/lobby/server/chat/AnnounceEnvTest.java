package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatTestServer.Joined;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.server.command.ConsoleSender;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /announce}: the format from messages.yml, every player, staff styles, console. */
@EnvTest
class AnnounceEnvTest {

    @TempDir
    Path dir;

    @AfterEach
    void stopChat() {
        ChatTestServer.stopAll();
    }

    @Test
    void reachesEveryoneEvenWithChatHidden(Env env) throws Exception {
        ChatTestServer server = ChatTestServer.start(env, dir, UnaryOperator.identity());
        Joined admin = server.join("Admin");
        Joined steve = server.join("Steve");
        server.services().settings().update(steve.player().getUuid(), settings -> settings.withChatVisible(false));

        env.process().command().execute(admin.player(), "announce <red>Server restart</red> in 5 minutes");

        assertEquals(List.of("ANNOUNCEMENT » Server restart in 5 minutes"), steve.lines());
        assertEquals(List.of("ANNOUNCEMENT » Server restart in 5 minutes"), admin.lines());
    }

    @Test
    void playersWithoutPermissionCannotAnnounce(Env env) throws Exception {
        ChatTestServer server = ChatTestServer.start(env, dir, UnaryOperator.identity());
        Joined steve = server.join("Steve");
        Joined alex = server.join("Alex");

        env.process().command().execute(steve.player(), "announce free ranks");

        assertEquals(List.of(), alex.lines());
    }

    @Test
    void theConsoleMayUseClickAndHover(Env env) throws Exception {
        ChatTestServer server = ChatTestServer.start(env, dir, UnaryOperator.identity());
        Joined steve = server.join("Steve");

        env.process().command().execute(new ConsoleSender(),
                "announce <click:open_url:'https://example.com'>Vote for us</click>");

        List<Component> received = steve.components();
        assertEquals(1, received.size());
        assertTrue(hasClick(received.getFirst()), "the link is clickable");
    }

    private static boolean hasClick(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
            return true;
        }
        return component.children().stream().anyMatch(AnnounceEnvTest::hasClick);
    }
}
