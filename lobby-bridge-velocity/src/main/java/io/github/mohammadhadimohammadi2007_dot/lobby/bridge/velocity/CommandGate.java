package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a console command requested by a lobby may run. Pure logic, no Velocity types,
 * so it is easy to test.
 */
final class CommandGate {

    /** Longest command accepted. */
    static final int MAX_LENGTH = 256;

    private final Set<String> allowedFirstWords;

    CommandGate(Set<String> allowedFirstWords) {
        this.allowedFirstWords = allowedFirstWords;
    }

    /**
     * Checks a command.
     *
     * @return {@code null} if it may run, otherwise the reason it was refused
     */
    String refusalReason(String command) {
        if (command.isBlank()) {
            return "empty command";
        }
        if (command.length() > MAX_LENGTH) {
            return "command is longer than " + MAX_LENGTH + " characters";
        }
        for (int i = 0; i < command.length(); i++) {
            if (Character.isISOControl(command.charAt(i))) {
                return "command contains control characters (line breaks are not allowed)";
            }
        }
        String first = firstWord(command);
        if (!allowedFirstWords.contains(first)) {
            return "command '" + first + "' is not in allowed-commands";
        }
        return null;
    }

    /** The command with surrounding spaces and a leading slash removed. */
    static String normalize(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static String firstWord(String command) {
        String normalized = normalize(command);
        int space = normalized.indexOf(' ');
        return (space < 0 ? normalized : normalized.substring(0, space)).toLowerCase(Locale.ROOT);
    }
}
