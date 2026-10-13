package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The lobby's PvP area, read only. Fights themselves are followed with the Pvp*Event events. Any thread. */
public interface Pvp {

    /** True if the area is set up and enabled in pvp.yml. */
    boolean available();

    /** True if the player is in the PvP area (and has the kit). */
    boolean inArea(Player player);

    /** Seconds of combat tag left, 0 if not in combat. */
    long combatSecondsLeft(Player player);

    /** A player's record; all zero for a player who never fought. */
    CompletableFuture<Record> stats(UUID player);

    /**
     * A player's PvP record.
     *
     * @param streak     kills since their last death
     * @param bestStreak their longest streak ever
     */
    record Record(int kills, int deaths, int streak, int bestStreak) {
    }
}
