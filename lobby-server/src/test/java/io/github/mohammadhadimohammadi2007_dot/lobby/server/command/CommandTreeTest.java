package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandSyntax;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every command of the real lobby: Minestom puts the subcommand names and argument ids of one command line
 * into a single map, so an argument named like a subcommand on its path ({@code /npc type <npc> <type>})
 * crashes the command when it runs.
 */
@EnvTest
class CommandTreeTest {

    @TempDir
    Path dir;

    @Test
    void noArgumentIsNamedLikeASubcommandOrAnotherArgumentOnItsPath(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Runnable stop = LobbyServerTestAccess.build(config);
        try {
            List<String> clashes = new ArrayList<>();
            List<Command> commands = List.copyOf(env.process().command().getCommands());
            assertFalse(commands.isEmpty());
            for (Command command : commands) {
                check(command, List.of(command.getName()), clashes);
            }
            assertEquals(List.of(), clashes);
        } finally {
            stop.run();
        }
    }

    @Test
    void npcTypeRunsWithoutCrashing(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Runnable stop = LobbyServerTestAccess.build(config);
        try {
            var commands = env.process().command();
            var console = new net.minestom.server.command.ConsoleSender();
            commands.execute(console, "npc create guide");
            // The command from the bug report: "type" was both the subcommand and its argument.
            var result = commands.execute(console, "npc type guide villager");
            assertEquals(net.minestom.server.command.builder.CommandResult.Type.SUCCESS, result.getType());
        } finally {
            stop.run();
        }
    }

    private static void check(Command command, List<String> path, List<String> clashes) {
        for (CommandSyntax syntax : command.getSyntaxes()) {
            Set<String> seen = new HashSet<>(path.subList(1, path.size()));
            for (Argument<?> argument : syntax.getArguments()) {
                if (!seen.add(argument.getId())) {
                    clashes.add("/" + String.join(" ", path) + " " + syntax.getSyntaxString()
                            + ": '" + argument.getId() + "' twice");
                }
            }
        }
        for (Command sub : command.getSubcommands()) {
            List<String> subPath = new ArrayList<>(path);
            subPath.add(sub.getName());
            check(sub, subPath, clashes);
        }
    }
}
