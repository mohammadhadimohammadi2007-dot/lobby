package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Runs a message through every enabled stage in order and reports the outcome. Runs on the chat thread.
 */
public final class ChatPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatPipeline.class);

    private final List<ChatStage> stages;
    private final BiConsumer<ChatMessage, ChatOutcome.Blocked> onBlocked;

    /**
     * @param stages    the stages, in order
     * @param onBlocked called for every blocked message (staff spy, chat log)
     */
    public ChatPipeline(List<ChatStage> stages, BiConsumer<ChatMessage, ChatOutcome.Blocked> onBlocked) {
        this.stages = List.copyOf(stages);
        this.onBlocked = onBlocked;
    }

    /** The stage names in order, for logs. */
    public List<String> stageNames() {
        return stages.stream().map(ChatStage::name).toList();
    }

    /** Runs the message through the pipeline. Never throws; a crashing stage blocks the message. */
    public ChatOutcome run(ChatMessage message, ChatConfig config) {
        for (ChatStage stage : stages) {
            if (!stage.enabled(config) || (message.relayed() && !stage.appliesToRelayed())) {
                continue;
            }
            ChatStage.Result result;
            try {
                result = stage.process(message, config);
            } catch (RuntimeException e) {
                LOGGER.error("Chat stage '{}' failed for a message from {}", stage.name(), message.senderName(), e);
                result = ChatStage.Result.block("internal error in " + stage.name(), null);
            }
            if (result.blocked()) {
                ChatOutcome.Blocked blocked = new ChatOutcome.Blocked(stage.name(), result.reason());
                if (result.feedback() != null && message.sender() != null) {
                    message.sender().sendMessage(result.feedback());
                }
                onBlocked.accept(message, blocked);
                return blocked;
            }
        }
        return new ChatOutcome.Sent(message.modifications(),
                message.rendered() == null ? 0 : message.rendered().recipients());
    }
}
