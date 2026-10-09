package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One player's chat and lobby choices (the chat settings store keeps every per-player setting). Immutable: every change makes a new copy, so it can be shared between threads.
 *
 * @param chatVisible false if the player hid public chat with {@code /chat toggle}
 * @param mentions    false if the player turned mention sounds and highlights off
 * @param persian     the player's {@code /chat persian} choice, or {@code null} to use the server default
 * @param channel     the channel the player writes in, or {@code null} for the default channel
 * @param ignored     players whose messages this player does not want to see
 * @param visibility  which players this player sees in the lobby ({@code all}, {@code staff}, {@code none}), or
 *                    {@code null} for the server default
 */
public record PlayerChatSettings(boolean chatVisible, boolean mentions, @Nullable Boolean persian,
                                 @Nullable String channel, Set<UUID> ignored, @Nullable String visibility) {

    /** Settings of a player who never changed anything. */
    public static final PlayerChatSettings DEFAULTS = new PlayerChatSettings(true, true, null, null, Set.of(), null);

    /** Most players a player can ignore, so one player cannot grow the settings without limit. */
    public static final int MAX_IGNORED = 200;

    public PlayerChatSettings {
        ignored = Set.copyOf(ignored);
    }

    public PlayerChatSettings(boolean chatVisible, boolean mentions, @Nullable Boolean persian, @Nullable String channel,
                              Set<UUID> ignored) {
        this(chatVisible, mentions, persian, channel, ignored, null);
    }

    public PlayerChatSettings withVisibility(@Nullable String mode) {
        return new PlayerChatSettings(chatVisible, mentions, persian, channel, ignored, mode);
    }

    public PlayerChatSettings withChatVisible(boolean visible) {
        return new PlayerChatSettings(visible, mentions, persian, channel, ignored, visibility);
    }

    public PlayerChatSettings withMentions(boolean enabled) {
        return new PlayerChatSettings(chatVisible, enabled, persian, channel, ignored, visibility);
    }

    public PlayerChatSettings withPersian(boolean enabled) {
        return new PlayerChatSettings(chatVisible, mentions, enabled, channel, ignored, visibility);
    }

    public PlayerChatSettings withChannel(@Nullable String newChannel) {
        return new PlayerChatSettings(chatVisible, mentions, persian, newChannel, ignored, visibility);
    }

    /** Adds or removes {@code player} from the ignore list. */
    public PlayerChatSettings withIgnored(UUID player, boolean ignore) {
        Set<UUID> updated = new LinkedHashSet<>(ignored);
        if (ignore) {
            updated.add(player);
        } else {
            updated.remove(player);
        }
        return new PlayerChatSettings(chatVisible, mentions, persian, channel, updated, visibility);
    }
}
