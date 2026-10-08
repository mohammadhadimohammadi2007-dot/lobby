package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import java.util.UUID;

/**
 * Keeps a record of chat messages and what happened to them. {@link #log} never blocks: entries are
 * written in the background in batches.
 */
public interface ChatLog {

    /** Does not keep anything. */
    ChatLog NONE = new ChatLog() {
        @Override
        public void log(Entry entry) {
        }

        @Override
        public String describe() {
            return "off";
        }
    };

    /**
     * One logged message.
     *
     * @param timeMillis  when it was sent
     * @param server      this lobby's name
     * @param channel     channel name
     * @param sender      sender's UUID
     * @param senderName  sender's name
     * @param original    exactly what the player typed
     * @param normalized  the filter's normalized form
     * @param outcome     {@code sent}, {@code modified} or {@code blocked}
     * @param reason      why it was blocked or modified, empty if sent unchanged
     */
    record Entry(long timeMillis, String server, String channel, UUID sender, String senderName, String original,
                 String normalized, String outcome, String reason) {
    }

    /** Queues an entry. Never blocks. */
    void log(Entry entry);

    /** Short description for logs and {@code /lobby info}. */
    String describe();

    /** Writes what is still queued and stops. */
    default void close() {
    }
}
