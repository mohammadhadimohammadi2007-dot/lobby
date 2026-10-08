package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.SettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Online players' chat settings in memory, loaded on join and saved on every change, both in the background.
 * Until a player's settings have loaded they count as {@link PlayerChatSettings#DEFAULTS}.
 */
public final class ChatSettingsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSettingsService.class);

    private final SettingsStore store;
    private final Map<UUID, PlayerChatSettings> online = new ConcurrentHashMap<>();

    public ChatSettingsService(SettingsStore store) {
        this.store = store;
    }

    /** Where settings are kept, for logs. */
    public String describe() {
        return store.describe();
    }

    /** Loads a player's settings in the background. */
    public void load(UUID player) {
        online.put(player, PlayerChatSettings.DEFAULTS);
        Async.run(() -> {
            try {
                store.load(player).ifPresent(settings -> online.computeIfPresent(player, (id, current) -> settings));
            } catch (Exception e) {
                LOGGER.warn("Could not load chat settings of {}: {}", player, e.getMessage());
            }
        });
    }

    /** Forgets a player who left. */
    public void forget(UUID player) {
        online.remove(player);
    }

    /** The player's settings; defaults if not loaded (yet). */
    public PlayerChatSettings get(UUID player) {
        return online.getOrDefault(player, PlayerChatSettings.DEFAULTS);
    }

    /** Changes a player's settings and saves them in the background. Returns the new settings. */
    public PlayerChatSettings update(UUID player, UnaryOperator<PlayerChatSettings> change) {
        PlayerChatSettings updated = online.compute(player, (id, current) ->
                change.apply(current == null ? PlayerChatSettings.DEFAULTS : current));
        Async.run(() -> {
            try {
                store.save(player, updated);
            } catch (Exception e) {
                LOGGER.warn("Could not save chat settings of {}: {}", player, e.getMessage());
            }
        });
        return updated;
    }
}
