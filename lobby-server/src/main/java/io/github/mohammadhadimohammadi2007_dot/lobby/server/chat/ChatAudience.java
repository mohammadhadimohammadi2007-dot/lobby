package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.ClientCapabilities;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.VariantKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import net.minestom.server.entity.Player;


/**
 * Decides who sees a chat message and which version they get. Used for delivery and for resending history
 * after a message is deleted, so both always agree.
 */
public final class ChatAudience {


    private final ChatServices services;

    public ChatAudience(ChatServices services) {
        this.services = services;
    }

    /** True if {@code viewer} should see {@code message}. */
    public boolean canSee(Player viewer, ChatMessage message) {
        if (viewer.getUuid().equals(message.senderId())) {
            return true;
        }
        ChatConfig config = services.chat();
        // With instance-only, chat stays inside the sender's own lobby instance.
        if (config.instanceOnly() && message.sender().getInstance() != viewer.getInstance()) {
            return false;
        }
        PlayerChatSettings settings = services.settings().get(viewer.getUuid());
        if (config.ignoreEnabled() && settings.ignored().contains(message.senderId())) {
            return false;
        }
        return !config.chatToggleEnabled() || settings.chatVisible();
    }

    /** The version of {@code message} that {@code viewer} gets. */
    public VariantKey variant(Player viewer, ChatMessage message) {
        ClientCapabilities client = services.bridge().capabilities(viewer);
        PlayerChatSettings settings = services.settings().get(viewer.getUuid());
        boolean mentioned = settings.mentions() && message.mentionedNames().containsKey(viewer.getUuid());
        return new VariantKey(client.legacy(), mentioned ? viewer.getUuid() : null, services.has(viewer, ChatPermissions.STAFF));
    }
}
