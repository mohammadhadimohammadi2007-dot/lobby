package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/** {@code lobby: <number>} moves the player to another lobby instance on this server. */
public record LobbyAction(int number) implements Action {

    @Override
    public Step run(ActionContext context) {
        context.services().lobbies().switchTo(context.player(), number);
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "lobby: " + number;
    }
}
