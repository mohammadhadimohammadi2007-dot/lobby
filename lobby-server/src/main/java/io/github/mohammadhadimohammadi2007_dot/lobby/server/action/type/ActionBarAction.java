package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/** {@code actionbar: <text>} shows a text above the hotbar. */
public record ActionBarAction(String text) implements Action {

    @Override
    public Step run(ActionContext context) {
        context.player().sendActionBar(context.services().text().render(text, context.player()));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "actionbar: " + text;
    }
}
