package io.github.mohammadhadimohammadi2007_dot.lobby.server.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.Consumer;

/**
 * Log lines on the console (see logback.xml). While the interactive console is running, lines are printed
 * above the prompt so typing is never interrupted. Warnings and errors are colored when the output is a
 * real terminal ({@code console.colors: auto}); Pterodactyl, Docker and log files get plain text.
 */
public final class ConsoleAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    /** {@code console.colors} in config.yml. */
    public enum Colors { AUTO, ON, OFF }

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GRAY = "\u001B[90m";
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");

    private static final PrintStream STDOUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    private static volatile Consumer<String> sink = STDOUT::print;
    private static volatile boolean colors = false;

    private Layout<ILoggingEvent> layout;

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    @Override
    public void start() {
        if (layout == null) {
            addError("No layout for the console appender");
            return;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String line = layout.doLayout(event);
        // Minestom's console replies come with colors; without colors they would show as "[38;5;7m".
        sink.accept(colors ? colored(line, event.getLevel()) : stripAnsi(line));
    }

    /** Where lines go: standard output, or above the prompt of the interactive console. */
    public static void sink(Consumer<String> target) {
        sink = target;
    }

    /** Back to plain standard output, e.g. when the interactive console stops. */
    public static void resetSink() {
        sink = STDOUT::print;
    }

    /**
     * Turns colors on or off.
     *
     * @param setting      {@code console.colors}
     * @param realTerminal true if the output is a terminal a person looks at
     */
    public static void colors(Colors setting, boolean realTerminal) {
        colors = useColors(setting, realTerminal);
    }

    static boolean useColors(Colors setting, boolean realTerminal) {
        return switch (setting) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> realTerminal;
        };
    }

    /** Parses {@code console.colors}; {@code null} for anything else. */
    public static Colors parseColors(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "auto" -> Colors.AUTO;
            case "on", "true" -> Colors.ON;
            case "off", "false" -> Colors.OFF;
            default -> null;
        };
    }

    /** {@code text} without ANSI escape sequences (colors, bold...). */
    public static String stripAnsi(String text) {
        return text.indexOf('\u001B') < 0 ? text : ANSI.matcher(text).replaceAll("");
    }

    static String colored(String line, Level level) {
        String color = level.isGreaterOrEqual(Level.ERROR) ? RED
                : level.isGreaterOrEqual(Level.WARN) ? YELLOW
                : level.isGreaterOrEqual(Level.INFO) ? "" : GRAY;
        if (color.isEmpty()) {
            return line;
        }
        // Keep the line break outside the color, so the prompt below is never colored.
        String body = line.endsWith(System.lineSeparator()) ? line.substring(0, line.length() - System.lineSeparator().length()) : line;
        return color + body + RESET + line.substring(body.length());
    }
}
