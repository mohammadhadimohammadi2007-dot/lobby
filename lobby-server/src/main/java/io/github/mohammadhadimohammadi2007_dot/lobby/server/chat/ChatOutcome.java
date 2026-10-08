package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import java.util.List;

/**
 * What happened to a chat message. Staff tools and the chat log use this to show why a message was
 * blocked or changed.
 */
public sealed interface ChatOutcome {

    /** Short word for logs: {@code sent}, {@code modified} or {@code blocked}. */
    String label();

    /** Why it was blocked or modified; empty if sent unchanged. */
    String reason();

    /**
     * The message was delivered.
     *
     * @param modifications what was changed (censored words, lower-cased...), empty if nothing
     * @param recipients    how many players received it on this lobby
     */
    record Sent(List<String> modifications, int recipients) implements ChatOutcome {
        public Sent {
            modifications = List.copyOf(modifications);
        }

        @Override
        public String label() {
            return modifications.isEmpty() ? "sent" : "modified";
        }

        @Override
        public String reason() {
            return String.join(", ", modifications);
        }
    }

    /**
     * The message was stopped.
     *
     * @param stage  the pipeline stage that stopped it, e.g. {@code filter}
     * @param reason why, e.g. {@code blocked word 'xyz'}
     */
    record Blocked(String stage, String reason) implements ChatOutcome {
        @Override
        public String label() {
            return "blocked";
        }
    }
}
