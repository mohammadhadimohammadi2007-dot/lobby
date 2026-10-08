package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Adds violation points and mutes players who reach the threshold (when enabled in chat.yml).
 *
 * <p>The player is always muted on this lobby right away. With the bridge, the configured command (for
 * example a LiteBans {@code tempmute}) also runs on the proxy, so the mute holds on the whole network.
 */
final class AutoMuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoMuter.class);

    private final ChatServices services;

    AutoMuter(ChatServices services) {
        this.services = services;
    }

    /** Adds points for a filter hit and mutes the sender if they reached the threshold. */
    void addPoints(ChatMessage message, int points, ChatConfig.AutoMute settings) {
        if (points <= 0) {
            return;
        }
        double total = services.violations().add(message.senderId(), points, settings.decayPerMinute(), System.nanoTime());
        if (!settings.enabled() || total < settings.threshold()) {
            return;
        }
        services.violations().reset(message.senderId());
        mute(message, settings);
    }

    private void mute(ChatMessage message, ChatConfig.AutoMute settings) {
        long until = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(settings.minutes());
        services.moderation().muteLocally(message.senderId(), until);
        LOGGER.info("Auto-muted {} for {} minutes (chat filter)", message.senderName(), settings.minutes());

        if (services.bridge().available() && !settings.proxyCommand().isBlank()) {
            String command = settings.proxyCommand()
                    .replace("<player>", message.senderName())
                    .replace("<minutes>", String.valueOf(settings.minutes()));
            services.bridge().requestCommand(command, "chat auto-mute").whenComplete((result, error) -> {
                if (error != null) {
                    LOGGER.warn("Proxy did not run the auto-mute command for {}: {}. The mute only applies on this lobby.",
                            message.senderName(), error.getMessage());
                } else if (!result.accepted()) {
                    LOGGER.warn("Proxy refused the auto-mute command for {}: {}. The mute only applies on this lobby.",
                            message.senderName(), result.detail());
                }
            });
        }
        if (message.sender() != null) {
            message.sender().sendMessage(services.text().message(MessageKey.CHAT_AUTO_MUTED, message.sender(),
                    Messages.text("minutes", settings.minutes())));
        }
        services.tellStaff(ChatPermissions.NOTIFY, services.text().message(MessageKey.CHAT_STAFF_AUTO_MUTED,
                Messages.text("player", message.senderName()), Messages.text("minutes", settings.minutes())));
    }
}
