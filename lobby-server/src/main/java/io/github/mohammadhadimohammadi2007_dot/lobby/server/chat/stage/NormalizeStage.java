package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.TextNormalizer;

/**
 * Stores the normalized form of the message (Persian spelling, no invisible characters, leetspeak...) for
 * the chat log. The filter normalizes again itself so it can map hits back to the original text.
 * Never changes what players see. Always on.
 */
public final class NormalizeStage implements ChatStage {

    @Override
    public String name() {
        return "normalize";
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        message.normalized(TextNormalizer.forFilter(message.text()).text());
        return Result.CONTINUE;
    }
}
