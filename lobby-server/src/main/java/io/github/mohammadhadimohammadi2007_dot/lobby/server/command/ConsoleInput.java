package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads commands typed into the server console. {@code stop} shuts the server down;
 * anything else runs as a command (for example {@code lobby reload}).
 */
public final class ConsoleInput {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleInput.class);
    private static final String STOP_COMMAND = "stop";

    private ConsoleInput() {
    }

    /** Starts reading the console on a background thread. */
    public static void start() {
        Thread thread = new Thread(ConsoleInput::readLoop, "console-input");
        thread.setDaemon(true);
        thread.start();
    }

    private static void readLoop() {
        CommandManager commands = MinecraftServer.getCommandManager();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.trim();
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                if (command.isEmpty()) {
                    continue;
                }
                if (command.equalsIgnoreCase(STOP_COMMAND)) {
                    LOGGER.info("Stopping the server...");
                    MinecraftServer.stopCleanly();
                    return;
                }
                commands.execute(commands.getConsoleSender(), command);
            }
        } catch (IOException e) {
            LOGGER.warn("Console input is not available: {}", e.getMessage());
        }
    }
}
