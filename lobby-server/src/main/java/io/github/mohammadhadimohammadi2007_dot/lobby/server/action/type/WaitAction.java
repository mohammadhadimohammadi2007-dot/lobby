package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/** {@code wait: <ticks>} pauses before the next action (20 ticks = 1 second). */
public record WaitAction(int ticks) implements Action {

    @Override
    public Step run(ActionContext context) {
        return Step.waitTicks(ticks);
    }

    @Override
    public String describe() {
        return "wait: " + ticks;
    }
}
