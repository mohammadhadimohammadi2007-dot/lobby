package io.github.mohammadhadimohammadi2007_dot.lobby.api.action;

/**
 * What happens after an action.
 *
 * @param stop      true to end the action list here (for example, the player cannot afford something)
 * @param waitTicks ticks to wait before the next action, 0 to go on right away
 */
public record ActionResult(boolean stop, int waitTicks) {

    /** Go on with the next action. */
    public static final ActionResult CONTINUE = new ActionResult(false, 0);
    /** End the list. */
    public static final ActionResult STOP = new ActionResult(true, 0);

    public ActionResult {
        if (waitTicks < 0) {
            throw new IllegalArgumentException("waitTicks must not be negative");
        }
    }

    /** Wait {@code ticks} ticks, then go on. */
    public static ActionResult waitTicks(int ticks) {
        return new ActionResult(false, ticks);
    }
}
