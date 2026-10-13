package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import net.minestom.server.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * A connector that first asks whether the player may leave now (for example not during a PvP fight). The rule
 * tells the player why when it says no.
 */
public record GuardedConnector(Connector inner, Predicate<Player> blocked) implements Connector {

    @Override
    public CompletableFuture<Boolean> connect(Player player, String server) {
        return blocked.test(player) ? CompletableFuture.completedFuture(false) : inner.connect(player, server);
    }

    @Override
    public CompletableFuture<Boolean> connectGroup(Player player, String group) {
        return blocked.test(player) ? CompletableFuture.completedFuture(false) : inner.connectGroup(player, group);
    }
}