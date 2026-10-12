package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.LobbyChatEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.LobbyEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;

/**
 * Lets addons change or cancel a message ({@link LobbyChatEvent}), after the filter and before formatting.
 * Skipped while no addon listens to events.
 */
public final class AddonStage implements ChatStage {

    @Override
    public String name() {
        return "addons";
    }

    @Override
    public boolean enabled(ChatConfig config) {
        return LobbyEvents.active();
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        if (message.sender() == null) {
            return Result.CONTINUE;
        }
        LobbyChatEvent event = LobbyEvents.fire(new LobbyChatEvent(message.sender(), message.original(), message.text()));
        if (event.isCancelled()) {
            return Result.block("cancelled by an addon", event.cancelFeedback());
        }
        message.modify(event.message(), "changed by an addon");
        return Result.CONTINUE;
    }
}
