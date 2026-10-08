package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;

/** {@code close_menu} closes the open menu. */
public record CloseMenuAction() implements Action {

    @Override
    public Step run(ActionContext context) {
        Async.onTickThread(() -> context.services().menus().close(context.player()));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "close_menu";
    }
}
