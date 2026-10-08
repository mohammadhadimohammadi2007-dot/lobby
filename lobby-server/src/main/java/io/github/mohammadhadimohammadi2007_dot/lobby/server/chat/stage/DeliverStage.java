package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatAudience;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.RenderedMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.VariantKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

/**
 * Sends the message to everyone who should see it, each in their version, pings mentioned players,
 * remembers it for staff tools, and sends network-channel messages to the other lobbies.
 */
public final class DeliverStage implements ChatStage {

    private final ChatServices services;
    private final ChatAudience audience;

    public DeliverStage(ChatServices services, ChatAudience audience) {
        this.services = services;
        this.audience = audience;
    }

    @Override
    public String name() {
        return "deliver";
    }

    @Override
    public boolean appliesToRelayed() {
        return true;
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        RenderedMessage rendered = message.rendered();
        Sound sound = sound(config.mentions().sound());
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!audience.canSee(viewer, message)) {
                continue;
            }
            VariantKey key = audience.variant(viewer, message);
            viewer.sendMessage(rendered.variant(key));
            rendered.addRecipient();
            boolean pinged = key.mentioned() != null
                    || (message.mentionEveryone() && !viewer.getUuid().equals(message.senderId())
                    && services.settings().get(viewer.getUuid()).mentions());
            if (pinged && message.notifyMentions()) {
                ping(viewer, message, sound);
            }
        }
        services.history().add(message, config.historySize());
        if (message.channel().network() && !message.relayed() && message.sender() != null) {
            relay(message, config);
        }
        return Result.CONTINUE;
    }

    private void ping(Player viewer, ChatMessage message, Sound sound) {
        if (sound != null) {
            viewer.playSound(sound);
        }
        viewer.sendActionBar(services.text().message(MessageKey.MENTION_ACTIONBAR, viewer,
                Messages.text("player", message.senderName())));
    }

    private void relay(ChatMessage message, ChatConfig config) {
        PlayerMeta meta = message.senderMeta();
        services.bridge().sendChat(message.sender(), new BridgeMessage.ChatRelay(message.id(), message.channel().name(),
                services.config().current().config().server().name(), message.senderId(), message.senderName(),
                meta.prefix(), meta.suffix(), meta.primaryGroup(), meta.meta(), message.text()));
    }

    /** The mention sound, or {@code null} if none or invalid. */
    private static Sound sound(String name) {
        if (name.isBlank()) {
            return null;
        }
        try {
            return Sound.sound(Key.key(name), Sound.Source.PLAYER, 1f, 1f);
        } catch (InvalidKeyException e) {
            return null;
        }
    }
}
