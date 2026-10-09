package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import net.minestom.server.entity.Player;

import java.util.function.ToIntFunction;

/**
 * What a viewer's real client can show, from its protocol version (reported by the proxy bridge; without
 * a bridge every client counts as the server's own version).
 */
final class ClientTiers {

    private final ToIntFunction<Player> protocolOf;

    ClientTiers(ToIntFunction<Player> protocolOf) {
        this.protocolOf = protocolOf;
    }

    /** 1.8-1.12: 16 colours, 16-character team prefixes, 32-character sidebar titles. */
    boolean legacyText(Player viewer) {
        return protocolOf.applyAsInt(viewer) < ProtocolVersions.V1_13;
    }

    /**
     * 1.20.3+: a sidebar line can be the score's own display name and the red numbers can be hidden.
     * Older clients need one team per line, whose prefix is the text.
     */
    boolean scoreDisplayNames(Player viewer) {
        return protocolOf.applyAsInt(viewer) >= ProtocolVersions.V1_20_3;
    }
}
