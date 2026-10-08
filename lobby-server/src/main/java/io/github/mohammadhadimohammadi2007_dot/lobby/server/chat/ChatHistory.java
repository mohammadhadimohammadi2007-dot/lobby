package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The last delivered messages on this lobby, so staff can delete one: chat is cleared and the rest is sent
 * again. Only used on the chat thread.
 */
public final class ChatHistory {

    private final Deque<ChatMessage> messages = new ArrayDeque<>();

    /** Remembers a delivered message, dropping the oldest above {@code limit}. */
    public void add(ChatMessage message, int limit) {
        messages.addLast(message);
        while (messages.size() > limit) {
            messages.removeFirst();
        }
    }

    /** Removes the message with this id; true if it was found. */
    public boolean remove(String id) {
        return messages.removeIf(message -> message.id().equalsIgnoreCase(id));
    }

    /** Oldest first. */
    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
