package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/** {@code open_menu: <name>} opens a menu from menus.yml. */
public record OpenMenuAction(String menu) implements Action {

    @Override
    public Step run(ActionContext context) {
        context.services().menus().open(context.player(), menu);
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "open_menu: " + menu;
    }
}
