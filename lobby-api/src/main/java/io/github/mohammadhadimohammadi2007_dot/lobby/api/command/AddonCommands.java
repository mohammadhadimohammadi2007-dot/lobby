package io.github.mohammadhadimohammadi2007_dot.lobby.api.command;

import net.minestom.server.command.builder.Command;
import org.jetbrains.annotations.Nullable;

/**
 * Commands of an addon, written with Minestom's {@link Command} classes.
 *
 * <p>They follow the lobby's rules like its own commands: {@code commands.yml} decides which players see and
 * may type them (add the command's name to a group there), and a permission given here is checked through
 * LuckPerms or the operators list. They are unregistered when the addon is disabled.
 */
public interface AddonCommands {

    /**
     * Registers a command.
     *
     * @param permission needed to see and run it, or {@code null} for everyone (commands.yml still applies)
     * @throws IllegalArgumentException if a command with that name or an alias already exists
     */
    void register(Command command, @Nullable String permission);

    /** Registers a command everyone may use (as far as commands.yml allows). */
    default void register(Command command) {
        register(command, null);
    }

    /** Removes a command this addon registered. */
    void unregister(Command command);
}
