package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;

/**
 * What a player's real client can display. Later phases use this to pick, for example,
 * text display entities (modern) or armor-stand holograms (legacy).
 *
 * @param protocolVersion the client's protocol version number
 * @param reported        true if the proxy bridge reported it; false means it is only a default guess
 */
public record ClientCapabilities(int protocolVersion, boolean reported) {

    /** Rough client generation. */
    public enum Tier {
        /** Older than 1.19.4 (for example 1.8.9 through ViaRewind). */
        LEGACY,
        /** 1.19.4 or newer. */
        MODERN
    }

    /** Which generation this client belongs to. */
    public Tier tier() {
        return protocolVersion < ProtocolVersions.V1_19_4 ? Tier.LEGACY : Tier.MODERN;
    }

    /** True for clients older than 1.19.4. */
    public boolean legacy() {
        return tier() == Tier.LEGACY;
    }
}
