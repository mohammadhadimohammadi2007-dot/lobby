package io.github.mohammadhadimohammadi2007_dot.lobby.common;

/**
 * Minecraft protocol version numbers the lobby cares about.
 *
 * <p>Full list: <a href="https://minecraft.wiki/w/Protocol_version">minecraft.wiki/w/Protocol_version</a>.
 */
public final class ProtocolVersions {

    /** Minecraft 1.8 to 1.8.9, the oldest client supported through ViaRewind. */
    public static final int V1_8 = 47;

    /**
     * Minecraft 1.19.4. Clients older than this lack newer display features
     * (for example text display entities), so the lobby treats them as "legacy".
     */
    public static final int V1_19_4 = 762;

    private ProtocolVersions() {
    }
}
