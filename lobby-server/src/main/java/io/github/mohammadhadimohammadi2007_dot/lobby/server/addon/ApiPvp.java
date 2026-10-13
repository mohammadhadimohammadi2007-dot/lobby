package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Pvp;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.PvpService;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The PvP area for addons, read only. */
final class ApiPvp implements Pvp {

    private final PvpService pvp;

    ApiPvp(PvpService pvp) {
        this.pvp = pvp;
    }

    @Override
    public boolean available() {
        return pvp.available();
    }

    @Override
    public boolean inArea(Player player) {
        return pvp.inArea(player);
    }

    @Override
    public long combatSecondsLeft(Player player) {
        return pvp.combatSecondsLeft(player);
    }

    @Override
    public CompletableFuture<Record> stats(UUID player) {
        return pvp.stats().stats(player).thenApply(stats ->
                new Record(stats.kills(), stats.deaths(), stats.streak(), stats.bestStreak()));
    }
}