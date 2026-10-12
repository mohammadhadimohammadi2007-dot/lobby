package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import org.jetbrains.annotations.Nullable;

/** Checks a command a lobby wants to run as a player before the proxy runs it. */
final class PlayerCommands {

    /** Longer than anything a player could type in chat. */
    static final int MAX_LENGTH = 256;

    private PlayerCommands() {
    }

    /**
     * The command as the proxy should run it: without the leading {@code /} and surrounding spaces. {@code null}
     * if it is empty, too long, or contains line breaks or other control characters.
     */
    static @Nullable String normalize(String command) {
        String value = command.strip();
        if (value.startsWith("/")) {
            value = value.substring(1).strip();
        }
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return null;
            }
        }
        return value;
    }
}
