package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;

/**
 * {@code connect: <server>} sends the player to a server of the network; {@code connect_group: <group>}
 * to the best server of a group, chosen by the proxy.
 */
public record ConnectAction(String target, boolean group) implements Action {

    @Override
    public Step run(ActionContext context) {
        String filled = context.services().text().placeholders().plainText(target, context.player()).strip();
        var connector = context.services().connector();
        var sent = group ? connector.connectGroup(context.player(), filled) : connector.connect(context.player(), filled);
        sent.whenComplete((ok, error) -> {
            if (error != null || !Boolean.TRUE.equals(ok)) {
                context.onConnectFailed().run();
            }
        });
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return (group ? "connect_group: " : "connect: ") + target;
    }
}
