package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.entity.Player;

/**
 * Checks the sender may write in the message's channel. For messages from another lobby, checks the
 * channel exists here and is a network channel; otherwise they are dropped quietly.
 */
public final class ChannelStage implements ChatStage {

    private final ChatServices services;

    public ChannelStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "channel";
    }

    @Override
    public boolean appliesToRelayed() {
        return true;
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        ChatConfig.Channel current = config.channels().get(message.channel().name());
        if (message.relayed()) {
            return current != null && current.network() ? Result.CONTINUE
                    : Result.block("channel " + message.channel().name() + " is not a network channel here", null);
        }
        Player sender = message.sender();
        if (current == null) {
            return Result.block("channel " + message.channel().name() + " is disabled",
                    services.text().message(MessageKey.CHANNEL_NO_PERMISSION, sender,
                            Messages.text("channel", message.channel().name())));
        }
        message.channel(current);
        if (!services.has(sender, current.permission()) || !services.has(sender, current.sendPermission())) {
            return Result.block("no permission for channel " + current.name(),
                    services.text().message(MessageKey.CHANNEL_NO_PERMISSION, sender, Messages.text("channel", current.name())));
        }
        return Result.CONTINUE;
    }
}
