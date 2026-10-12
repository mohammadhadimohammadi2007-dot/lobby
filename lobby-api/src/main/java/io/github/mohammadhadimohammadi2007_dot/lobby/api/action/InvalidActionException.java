package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

/** An action entry that cannot be read. The message is shown to the admin, so say what is wrong in plain words. */
public class InvalidActionException extends Exception {

    public InvalidActionException(String message) {
        super(message);
    }
}
