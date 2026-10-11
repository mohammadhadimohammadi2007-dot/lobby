package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import org.jetbrains.annotations.Nullable;

/**
 * One line of {@code /setup-lobby setup} or {@code /setup-lobby doctor}.
 *
 * @param status  how it looks
 * @param title   what was checked, short
 * @param detail  what was found, and what to do about it
 * @param command a command that fixes it or goes on from here (clickable in game), or {@code null}
 */
public record Check(Status status, String title, String detail, @Nullable String command) {

    /** How a check came out. */
    public enum Status {
        /** Done. */
        OK("✔"),
        /** Works, but should be looked at. */
        WARN("⚠"),
        /** Broken or missing. */
        FAIL("✘"),
        /** Not a problem; for information or not set up (optional). */
        INFO("•");

        private final String symbol;

        Status(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    public static Check ok(String title, String detail) {
        return new Check(Status.OK, title, detail, null);
    }

    public static Check warn(String title, String detail, @Nullable String command) {
        return new Check(Status.WARN, title, detail, command);
    }

    public static Check fail(String title, String detail, @Nullable String command) {
        return new Check(Status.FAIL, title, detail, command);
    }

    public static Check info(String title, String detail, @Nullable String command) {
        return new Check(Status.INFO, title, detail, command);
    }
}
