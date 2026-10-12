package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

/**
 * One action entry of an addon's type, ready to run.
 *
 * <p>Runs on a background (virtual) thread, so it may block briefly, but must hand anything that touches the
 * world or a player's inventory to the tick thread ({@code scheduler().nextTick(...)}). Sending messages and
 * sounds is fine from any thread. An exception disables the addon and ends the action list.
 */
@FunctionalInterface
public interface CustomAction {

    /** Runs the action and says how the list goes on. */
    ActionResult run(ActionRun run);
}
