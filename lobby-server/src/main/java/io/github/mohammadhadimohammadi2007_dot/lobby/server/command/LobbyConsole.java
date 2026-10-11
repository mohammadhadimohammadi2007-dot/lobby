package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.logging.ConsoleAppender;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The server console, like Spigot and Paper: tab completion from the real command tree, command history
 * ({@code logs/.console_history}), colored warnings and errors, and log lines printed above the prompt.
 * Ctrl+C stops the server cleanly.
 *
 * <p>When the server does not run in a terminal (Pterodactyl, Docker, systemd, a pipe), there is no prompt
 * and no color: commands are simply read line by line from standard input.
 *
 * <p>Commands run on the tick thread, like commands from players.
 */
public final class LobbyConsole {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyConsole.class);
    private static final String PROMPT = "> ";

    private final CommandManager commands;
    private final Path historyFile;
    private volatile ConsoleAppender.Colors colors;
    private volatile boolean interactive;
    private volatile Terminal terminal;

    /**
     * @param dataDir the server folder; the history is kept in {@code logs/.console_history}
     * @param colors  {@code console.colors} from config.yml
     */
    public LobbyConsole(CommandManager commands, Path dataDir, ConsoleAppender.Colors colors) {
        this.commands = commands;
        this.historyFile = dataDir.resolve("logs").resolve(".console_history");
        this.colors = colors;
    }

    /** Starts reading commands on a background thread. */
    public void start() {
        Thread thread = new Thread(this::run, "lobby-console");
        thread.setDaemon(true);
        thread.start();
    }

    /** Applies a changed {@code console.colors} (after a reload). */
    public void colors(ConsoleAppender.Colors setting) {
        colors = setting;
        ConsoleAppender.colors(setting, interactive);
    }

    /** Gives the terminal back as it was. Called when the server stops. */
    public void stop() {
        ConsoleAppender.resetSink();
        Terminal current = terminal;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Stopping anyway.
            }
        }
    }

    private void run() {
        // System.console() is null when input or output is redirected: no terminal, no prompt.
        if (System.console() == null || !System.console().isTerminal()) {
            ConsoleAppender.colors(colors, false);
            readPlain();
            return;
        }
        try {
            terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("The console has no tab completion ({}); commands can still be typed.", e.getMessage());
            ConsoleAppender.colors(colors, false);
            readPlain();
            return;
        }
        interactive = !Terminal.TYPE_DUMB.equals(terminal.getType())
                && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType());
        ConsoleAppender.colors(colors, interactive);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("lobby")
                .completer(new ConsoleCompleter(commands))
                .variable(LineReader.HISTORY_FILE, historyFile)
                // "!" and "^" are normal characters in commands (messages, MiniMessage).
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();
        ConsoleAppender.sink(text -> reader.printAbove(text.endsWith(System.lineSeparator())
                ? text.substring(0, text.length() - System.lineSeparator().length()) : text));
        while (true) {
            String line;
            try {
                line = reader.readLine(PROMPT);
            } catch (UserInterruptException e) {
                LOGGER.info("Ctrl+C: stopping the server...");
                ConsoleAppender.resetSink();
                MinecraftServer.stopCleanly();
                return;
            } catch (EndOfFileException e) {
                // Ctrl+D or the terminal closed (also when the server stops).
                ConsoleAppender.resetSink();
                return;
            } catch (RuntimeException e) {
                ConsoleAppender.resetSink();
                LOGGER.warn("The console stopped reading: {}", e.getMessage());
                return;
            }
            run(line);
        }
    }

    private void readPlain() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                run(line);
            }
        } catch (IOException e) {
            LOGGER.warn("Console input is not available: {}", e.getMessage());
        }
    }

    /** Runs one typed line as the console, on the tick thread. */
    private void run(String line) {
        String command = line.strip();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            return;
        }
        String toRun = command;
        MinecraftServer.getSchedulerManager().scheduleNextTick(
                () -> commands.execute(commands.getConsoleSender(), toRun));
    }
}
