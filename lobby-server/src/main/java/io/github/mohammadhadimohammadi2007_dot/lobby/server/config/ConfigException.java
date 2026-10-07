package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

/**
 * A configuration problem the server cannot safely work around, for example an unknown
 * connection mode. The message is written for server owners and can be shown as-is.
 */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
