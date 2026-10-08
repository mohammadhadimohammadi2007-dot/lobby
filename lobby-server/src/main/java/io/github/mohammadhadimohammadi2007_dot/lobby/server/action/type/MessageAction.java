package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/** {@code message: <text>} sends a chat message (MiniMessage, legacy colors and placeholders). */
public record MessageAction(String text) implements Action {

    @Override
    public Step run(ActionContext context) {
        context.player().sendMessage(context.services().text().render(text, context.player()));
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "message: " + text;
    }
}
