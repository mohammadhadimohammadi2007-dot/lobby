package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.viaversion.viaversion.api.Via;

import java.util.UUID;

/**
 * Asks ViaVersion for a player's real client version. Only loaded when ViaVersion is installed,
 * so the plugin still works without it.
 */
final class ViaVersionHook {

    private ViaVersionHook() {
    }

    /** The protocol version of the player's actual client, e.g. 47 for 1.8.9. */
    static int clientProtocol(UUID playerId) {
        return Via.getAPI().getPlayerProtocolVersion(playerId).getVersion();
    }
}
