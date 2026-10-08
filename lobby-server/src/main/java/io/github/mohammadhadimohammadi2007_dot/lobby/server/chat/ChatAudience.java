package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.ClientCapabilities;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render.VariantKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import net.minestom.server.entity.Player;

import java.util.Locale;
import java.util.Set;

/**
 * Decides who sees a chat message and which version they get. Used for delivery and for resending history
 * after a message is deleted, so both always agree.
 */
public final class ChatAudience {

    /** Channels players still see when they hid the chat with /chat toggle. */
    private static final Set<String> ALWAYS_VISIBLE = Set.of("staff", "announce");
    /** Languages whose clients may order right-to-left text themselves (see persian.trust-rtl-clients). */
    private static final Set<String> RTL_LANGUAGES = Set.of("fa", "ar", "he", "ur");

    private final ChatServices services;

    public ChatAudience(ChatServices services) {
        this.services = services;
    }

    /** True if {@code viewer} should see {@code message}. */
    public boolean canSee(Player viewer, ChatMessage message) {
        ChatConfig.Channel channel = message.channel();
        if (!services.has(viewer, channel.permission())) {
            return false;
        }
        if (viewer.getUuid().equals(message.senderId())) {
            return true;
        }
        // An instance-only channel stays inside the sender's own lobby instance.
        if (channel.instanceOnly() && message.sender() != null
                && message.sender().getInstance() != viewer.getInstance()) {
            return false;
        }
        PlayerChatSettings settings = services.settings().get(viewer.getUuid());
        ChatConfig config = services.chat();
        boolean staffChannel = channel.name().equals("staff");
        if (config.ignoreEnabled() && !staffChannel && settings.ignored().contains(message.senderId())) {
            return false;
        }
        return !config.chatToggleEnabled() || settings.chatVisible() || ALWAYS_VISIBLE.contains(channel.name());
    }

    /** The version of {@code message} that {@code viewer} gets. */
    public VariantKey variant(Player viewer, ChatMessage message) {
        ClientCapabilities client = services.bridge().capabilities(viewer);
        PlayerChatSettings settings = services.settings().get(viewer.getUuid());
        boolean mentioned = settings.mentions() && message.mentionedNames().containsKey(viewer.getUuid());
        return new VariantKey(client.legacy(), wantsPersian(viewer, settings, client),
                mentioned ? viewer.getUuid() : null, services.has(viewer, ChatPermissions.STAFF));
    }

    /** True if Persian text should be fixed for this viewer. */
    public boolean wantsPersian(Player viewer, PlayerChatSettings settings, ClientCapabilities client) {
        ChatConfig.Persian persian = services.chat().persian();
        boolean wanted = settings.persian() != null ? settings.persian() : persian.defaultOn();
        if (!wanted || !persian.trustRtlClients()) {
            return wanted;
        }
        String language = viewer.getSettings().locale().getLanguage().toLowerCase(Locale.ROOT);
        return !(RTL_LANGUAGES.contains(language) && client.protocolVersion() >= ProtocolVersions.V1_16);
    }
}
