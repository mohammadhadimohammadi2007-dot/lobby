package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

/**
 * Answers "may this player do X?" and "what is this player's rank?".
 *
 * <p>Two implementations: {@link OperatorPermissionService} (the {@code operators:} list in config.yml)
 * and the LuckPerms one. The rest of the server only talks to this interface.
 * Both methods must be fast and must not block: they are called on the tick thread.
 */
public interface PermissionService {

    /** Short name for logs and {@code /lobby info}. */
    String name();

    /** True if {@code player} has {@code permission}. */
    boolean hasPermission(Player player, String permission);

    /** Rank prefix, suffix, group and meta of {@code player}. */
    PlayerMeta meta(Player player);

    /** The console may do everything; players need the permission. */
    default boolean hasPermission(CommandSender sender, String permission) {
        return !(sender instanceof Player player) || hasPermission(player, permission);
    }

    /** Releases resources. Called once on shutdown. */
    default void shutdown() {
    }
}
