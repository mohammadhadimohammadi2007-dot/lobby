package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;

/** Prepares the message for rendering; each version is rendered once, on first use, by the deliver stage. */
public final class RenderStage implements ChatStage {

    private final ChatServices services;

    public RenderStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "render";
    }

    @Override
    public boolean appliesToRelayed() {
        return true;
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        message.rendered(services.renderer().prepare(message, message.styling(), message.emojis()));
        return Result.CONTINUE;
    }
}
