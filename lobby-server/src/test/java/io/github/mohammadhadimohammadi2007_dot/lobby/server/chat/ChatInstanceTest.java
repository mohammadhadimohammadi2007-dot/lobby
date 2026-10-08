package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatTestServer.Joined;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Chat with more than one lobby instance: an {@code instance-only} channel stays inside the sender's
 * own lobby, every other channel does not.
 */
@EnvTest
class ChatInstanceTest {

    @TempDir
    Path dir;

    /** A second lobby instance of the same map, as {@code LobbyInstances} creates them. */
    private static Instance secondLobby(ChatTestServer server) {
        return MinecraftServer.getInstanceManager().createSharedInstance((InstanceContainer) server.instance);
    }

    @Test
    void instanceOnlyChannelStaysInTheSendersLobby(Env env) throws Exception {
        // "local" ships with instance-only: true.
        ChatTestServer server = ChatTestServer.start(env, dir, UnaryOperator.identity());
        Joined steve = server.join("Steve");
        Joined neighbour = server.join("Neighbour");
        Joined faraway = server.join("Faraway", secondLobby(server));

        server.say(steve, "hello there");

        assertEquals(List.of("Steve: hello there"), steve.lines());
        assertEquals(List.of("Steve: hello there"), neighbour.lines());
        assertEquals(List.of(), faraway.lines(), "another lobby instance is another world");
    }

    @Test
    void otherChannelsReachEveryLobby(Env env) throws Exception {
        // The global channel is not instance-only, so it crosses the instances of this server.
        ChatTestServer server = ChatTestServer.start(env, dir,
                chat -> chat.replace("send-permission: \"lobby.chat.channel.global\"", "send-permission: \"\""));
        Joined steve = server.join("Steve");
        Joined faraway = server.join("Faraway", secondLobby(server));

        server.say(steve, "!hello everyone");

        assertEquals(List.of("[G] Steve: hello everyone"), steve.lines());
        assertEquals(List.of("[G] Steve: hello everyone"), faraway.lines());
    }
}
