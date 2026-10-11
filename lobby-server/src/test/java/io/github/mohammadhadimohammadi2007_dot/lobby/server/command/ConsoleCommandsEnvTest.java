package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every admin command answers the console: it works, asks for what it needs, or says it is in-game only. */
@EnvTest
class ConsoleCommandsEnvTest {

    @TempDir
    Path dir;

    /** A console that remembers what it was told. */
    private static final class RecordingConsole extends ConsoleSender {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendMessage(Component message) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(message));
        }

        @Override
        public void sendMessage(String message) {
            lines.add(message);
        }

        String last() {
            return lines.isEmpty() ? "" : lines.getLast();
        }
    }

    /** Commands not run here: stopping would end the test server. */
    private static final Set<String> SKIPPED = Set.of("stop");

    @Test
    void everyCommandAndSubcommandAnswersTheConsole(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Runnable stop = LobbyServerTestAccess.build(config);
        try {
            var commands = env.process().command();
            List<String> silent = new ArrayList<>();
            for (Command command : List.copyOf(commands.getCommands())) {
                if (SKIPPED.contains(command.getName())) {
                    continue;
                }
                for (String path : paths(command, command.getName())) {
                    RecordingConsole console = new RecordingConsole();
                    commands.execute(console, path);
                    // Reload and import answer from a background thread, on a later tick.
                    env.tickWhile(console.lines::isEmpty, java.time.Duration.ofSeconds(10));
                    if (console.lines.isEmpty()) {
                        silent.add(path);
                    }
                }
            }
            assertEquals(List.of(), silent, "commands that said nothing to the console");
        } finally {
            stop.run();
        }
    }

    private static List<String> paths(Command command, String path) {
        List<String> result = new ArrayList<>();
        result.add(path);
        for (Command sub : command.getSubcommands()) {
            result.addAll(paths(sub, path + " " + sub.getName()));
        }
        return result;
    }

    @Test
    void theConsoleGivesCoordinatesOrAPlayerName(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Runnable stop = LobbyServerTestAccess.build(config);
        try {
            var commands = env.process().command();
            RecordingConsole console = new RecordingConsole();
            Player steve = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"))
                    .connect(env.createFlatInstance(), new Pos(0, 42, 0));

            commands.execute(console, "npc create guide");
            assertTrue(console.last().contains("/npc create <name> <x> <y> <z>"), console.last());
            commands.execute(console, "npc create guide 10 65 10");
            assertTrue(console.last().contains("guide"), console.last());
            commands.execute(console, "hologram create welcome 0 66 0");
            assertTrue(console.last().contains("welcome"), console.last());
            commands.execute(console, "hologram moveto welcome 1 66 1");
            assertTrue(console.last().toLowerCase().contains("moved"), console.last());

            commands.execute(console, "gamemode creative");
            assertTrue(console.last().contains("name a player"), console.last());
            commands.execute(console, "gamemode creative Steve");
            assertEquals(GameMode.CREATIVE, steve.getGameMode());
            commands.execute(console, "tp Steve 5 50 5");
            assertEquals(new Pos(5, 50, 5), steve.getPosition().withView(0, 0));
            commands.execute(console, "fly Steve");
            assertTrue(console.last().contains("Steve"), console.last());

            commands.execute(console, "setup-lobby setspawn 1.5 70 2.5");
            env.tickWhile(() -> !console.last().contains("Spawn set"), java.time.Duration.ofSeconds(5));
            assertTrue(Files.readString(dir.resolve(ConfigManager.CONFIG_FILE)).contains("x: 1.50"));

            commands.execute(console, "npc teleport guide");
            assertTrue(console.last().contains("in game"), console.last());
            commands.execute(console, "list");
            assertTrue(console.last().contains("Steve"), console.last());
        } finally {
            stop.run();
        }
    }
}
