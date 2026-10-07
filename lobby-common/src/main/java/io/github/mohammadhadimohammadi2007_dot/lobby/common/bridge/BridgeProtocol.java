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
     * Current protocol version. Increase it whenever the binary format changes in a way that
     * older readers cannot understand. Readers reject messages with a different version.
     */
    public static final int VERSION = 1;

    /** Longest string (in UTF-8 bytes) a message may contain, e.g. a server name. */
    public static final int MAX_STRING_BYTES = 256;

    /** Largest number of entries in any list or map inside a message. */
    public static final int MAX_ENTRIES = 1024;

    /** Largest total message size in bytes. Bigger messages are rejected without reading them. */
    public static final int MAX_MESSAGE_BYTES = 256 * 1024;

    private BridgeProtocol() {
    }
}
