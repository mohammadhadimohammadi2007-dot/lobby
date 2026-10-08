package io.github.mohammadhadimohammadi2007_dot.lobby.server.data;

/** A broken entry in a data file. The message says what is wrong in plain words. */
public final class DataException extends Exception {

    public DataException(String message) {
        super(message);
    }
}
