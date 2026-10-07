package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells the chat system (Phase 2) whether a player is muted. Answers come from memory, so both
 * methods are safe and fast to call on the tick thread.
 */
public interface MuteService {

    /** Used when LiteBans is disabled: nobody is muted. */
    MuteService NONE = new MuteService() {
        @Override
        public Optional<Punishment> activeMute(UUID playerId) {
            return Optional.empty();
        }
    };

    /** The player's current mute, if any. Only knows about online players. */
    Optional<Punishment> activeMute(UUID playerId);

    /** True if the player is muted right now. */
    default boolean isMuted(UUID playerId) {
        return activeMute(playerId).isPresent();
    }
}
