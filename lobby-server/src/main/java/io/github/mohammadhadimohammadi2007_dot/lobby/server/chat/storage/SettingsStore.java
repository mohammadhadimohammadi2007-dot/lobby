package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import java.util.Optional;
import java.util.UUID;

/**
 * Where player chat settings are kept: the shared database, or local files when there is none.
 * Both methods block; callers run them on virtual threads.
 */
public interface SettingsStore {

    /** The saved settings, or empty if the player never changed anything. */
    Optional<PlayerChatSettings> load(UUID player) throws Exception;

    /** Saves the settings, replacing older ones. */
    void save(UUID player, PlayerChatSettings settings) throws Exception;

    /** Short description for logs, e.g. "database table lobby_chat_settings". */
    String describe();
}
