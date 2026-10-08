package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One step of the chat pipeline. Stages run in this order:
 * {@code mute -> cooldown -> anti-spam -> normalize -> filter -> channel -> format -> render -> deliver -> log}.
 * Each stage does one thing and can be turned off by its own chat.yml option.
 */
public interface ChatStage {

    /** Name for logs and staff tools, e.g. {@code filter}. */
    String name();

    /** False if chat.yml turns this stage off. Disabled stages are skipped. */
    default boolean enabled(ChatConfig config) {
        return true;
    }

    /**
     * Also run for messages relayed from another lobby? Those were already checked where they were written,
     * so checks like mute, spam and filter are skipped; formatting, rendering and delivery are not.
     */
    default boolean appliesToRelayed() {
        return false;
    }

    /**
     * Processes the message.
     *
     * @return {@link Result#CONTINUE}, or a block with the reason
     */
    Result process(ChatMessage message, ChatConfig config);

    /**
     * What a stage decided.
     *
     * @param blocked  true to stop the message
     * @param reason   why, for staff and the log
     * @param feedback what the sender is told, or {@code null} to tell them nothing
     */
    record Result(boolean blocked, String reason, @Nullable Component feedback) {

        /** Go on with the next stage. */
        public static final Result CONTINUE = new Result(false, "", null);

        /** Stop the message. */
        public static Result block(String reason, @Nullable Component feedback) {
            return new Result(true, reason, feedback);
        }
    }
}
