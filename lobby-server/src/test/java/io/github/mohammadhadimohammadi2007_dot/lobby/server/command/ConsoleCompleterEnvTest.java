package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Console tab completion on the real lobby's command tree. */
@EnvTest
class ConsoleCompleterEnvTest {

    @TempDir
    Path dir;

    @Test
    void completesCommandsSubcommandsAndFixedWords(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Runnable stop = LobbyServerTestAccess.build(config);
        try {
            ConsoleCompleter completer = new ConsoleCompleter(env.process().command());

            List<String> roots = completer.suggestions(List.of("se"), "se");
            assertTrue(roots.contains("setup-lobby") && roots.contains("npc") && roots.contains("hd"), roots.toString());

            List<String> subs = completer.suggestions(List.of("setup-lobby", ""), "setup-lobby ");
            assertTrue(subs.containsAll(List.of("reload", "info", "setspawn")), subs.toString());

            List<String> poses = completer.suggestions(List.of("npc", "pose", "guide", ""), "npc pose guide ");
            assertTrue(poses.contains("crouching") && poses.contains("sitting"), poses.toString());

            assertTrue(completer.suggestions(List.of("nosuchcommand", ""), "nosuchcommand ").isEmpty());
        } finally {
            stop.run();
        }
    }
}
