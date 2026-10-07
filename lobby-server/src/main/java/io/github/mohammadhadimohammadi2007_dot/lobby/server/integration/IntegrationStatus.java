package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration;

/**
 * State of one optional integration, shown at startup and in {@code /lobby info}.
 *
 * @param name   display name, e.g. {@code LiteBans}
 * @param state  whether it is running
 * @param detail short extra info, e.g. the reason it failed; may be empty
 */
public record IntegrationStatus(String name, State state, String detail) {

    /** Whether an integration is running. */
    public enum State {
        /** Turned off in integrations.yml. */
        DISABLED,
        /** Turned on and working. */
        ACTIVE,
        /** Turned on but could not start. The server keeps running without it. */
        FAILED
    }

    public static IntegrationStatus disabled(String name) {
        return new IntegrationStatus(name, State.DISABLED, "");
    }

    public static IntegrationStatus active(String name, String detail) {
        return new IntegrationStatus(name, State.ACTIVE, detail);
    }

    public static IntegrationStatus failed(String name, String reason) {
        return new IntegrationStatus(name, State.FAILED, reason);
    }

    /** One-line description for logs. */
    public String describe() {
        return switch (state) {
            case DISABLED -> name + ": disabled";
            case ACTIVE -> name + ": active" + (detail.isEmpty() ? "" : " (" + detail + ")");
            case FAILED -> name + ": FAILED - " + detail;
        };
    }
}
