package io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge;

/** Thrown when bytes on the bridge channel are not a valid {@link BridgeMessage}. */
public final class BridgeFormatException extends Exception {

    public BridgeFormatException(String message) {
        super(message);
    }

    public BridgeFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
