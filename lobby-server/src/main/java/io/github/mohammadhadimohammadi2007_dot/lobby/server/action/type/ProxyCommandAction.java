package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ProxyCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code proxy_command: <command>} runs a command on the proxy as the player, with their permissions there,
 * even if the lobby has a command with the same name (for example a proxy's own {@code /lobby}).
 */
public record ProxyCommandAction(String command) implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyCommandAction.class);

    @Override
    public Step run(ActionContext context) {
        String filled = ProxyCommands.clean(context.services().text().placeholders().plainText(command, context.player()));
        if (filled == null) {
            LOGGER.warn("'{}' of {} is empty or longer than {} characters once its placeholders are filled in",
                    describe(), context.source(), ProxyCommands.MAX_LENGTH);
            return Step.CONTINUE;
        }
        context.services().proxyCommands().run(context.player(), filled, context.source());
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        return "proxy_command: " + command;
    }
}
