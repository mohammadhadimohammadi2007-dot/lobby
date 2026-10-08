package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

/**
 * Constants of the bridge protocol spoken between the proxy plugin and the lobby server.
 *
 * <p>The full wire format is documented in {@code docs/bridge-protocol.md}.
 */
public final class BridgeProtocol {

    /** Plugin message channel used by the bridge. */
    public static final String CHANNEL = "lobby:bridge";

    /**
     * Current protocol version. Increase it only when an existing message type changes its layout.
     * Readers reject messages with a different version. New message types do not need a new version:
     * readers ignore types they do not know.
     */
    public static final int VERSION = 1;

    /** Longest short string (in UTF-8 bytes), e.g. a server or player name. */
    public static final int MAX_STRING_BYTES = 256;

    /** Longest long text (in UTF-8 bytes), e.g. a chat message or a command. Persian letters take 2 bytes. */
    public static final int MAX_TEXT_BYTES = 4096;

    /** Largest number of entries in any list or map inside a message. */
    public static final int MAX_ENTRIES = 1024;

    /** Largest total message size in bytes. Bigger messages are rejected without reading them. */
    public static final int MAX_MESSAGE_BYTES = 256 * 1024;

    private BridgeProtocol() {
    }
}
