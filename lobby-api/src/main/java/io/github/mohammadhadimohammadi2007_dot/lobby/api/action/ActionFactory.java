package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

/** Reads the argument of one action entry, when the config or data file is read. */
@FunctionalInterface
public interface ActionFactory {

    /**
     * @param argument everything after {@code type:}, trimmed; empty if there was none. Placeholders in it
     *                 are not filled in yet: use {@link ActionRun#fill} when the action runs
     * @throws InvalidActionException if the argument is wrong; its message is shown to the admin, and only
     *                                this entry is skipped
     */
    CustomAction create(String argument) throws InvalidActionException;
}
