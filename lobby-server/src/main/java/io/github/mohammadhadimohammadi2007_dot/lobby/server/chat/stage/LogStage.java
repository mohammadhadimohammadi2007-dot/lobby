package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.ChatLog;

/**
 * Logs delivered messages (blocked ones are logged by the pipeline). Messages from other lobbies are logged
 * where they were written. Turned off by the storage options in chat.yml.
 */
public final class LogStage implements ChatStage {

    private final ChatServices services;

    public LogStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        boolean modified = !message.modifications().isEmpty();
        services.log().log(entry(message, modified ? "modified" : "sent", String.join(", ", message.modifications()),
                services.config().current().config().server().name()));
        return Result.CONTINUE;
    }

    /** A log entry for {@code message}. */
    public static ChatLog.Entry entry(ChatMessage message, String outcome, String reason, String server) {
        return new ChatLog.Entry(message.receivedMillis(), server, message.channel().name(), message.senderId(),
                message.senderName(), message.original(), message.normalized(), outcome, reason);
    }
}
