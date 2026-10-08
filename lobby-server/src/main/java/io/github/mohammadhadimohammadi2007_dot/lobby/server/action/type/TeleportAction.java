package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@code teleport: <x> <y> <z> [yaw] [pitch]} or {@code teleport: spawn}. Moves the player inside the
 * lobby they are in.
 *
 * @param target where to go, or {@code null} for the spawn point from config.yml
 */
public record TeleportAction(@Nullable Pos target) implements Action {

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        Pos destination = target != null ? target
                : SpawnListener.spawnPosition(context.services().config().current().config());
        Async.onTickThread(() -> {
            if (player.isOnline()) {
                player.teleport(destination);
            }
        });
        return Step.CONTINUE;
    }

    @Override
    public String describe() {
        if (target == null) {
            return "teleport: spawn";
        }
        return "teleport: " + target.x() + " " + target.y() + " " + target.z() + " " + target.yaw() + " " + target.pitch();
    }
}
