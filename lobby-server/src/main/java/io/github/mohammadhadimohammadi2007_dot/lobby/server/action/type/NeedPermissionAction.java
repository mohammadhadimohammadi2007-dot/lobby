package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@code need_permission: <permission> [message]} stops the list for players without the permission and
 * tells them why: the given message, or "no-permission" from messages.yml.
 */
public record NeedPermissionAction(String permission, @Nullable String message) implements Action {

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        if (context.services().permissions().hasPermission(player, permission)) {
            return Step.CONTINUE;
        }
        player.sendMessage(message != null
                ? context.services().text().render(message, player)
                : context.services().text().message(MessageKey.NO_PERMISSION, player));
        return Step.STOP;
    }

    @Override
    public String describe() {
        return "need_permission: " + permission + (message == null ? "" : " " + message);
    }
}
