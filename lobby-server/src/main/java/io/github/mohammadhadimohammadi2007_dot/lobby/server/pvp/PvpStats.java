package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import java.util.UUID;

/**
 * One player's PvP record.
 *
 * @param name       the name they last fought with, for leaderboards
 * @param streak     kills since they last died
 * @param bestStreak the longest streak they ever had
 */
public record PvpStats(UUID id, String name, int kills, int deaths, int streak, int bestStreak) {

    /** A player who never fought. */
    public static PvpStats none(UUID id, String name) {
        return new PvpStats(id, name, 0, 0, 0, 0);
    }

    /** After killing someone. */
    public PvpStats withKill(String currentName) {
        int newStreak = streak + 1;
        return new PvpStats(id, currentName, kills + 1, deaths, newStreak, Math.max(bestStreak, newStreak));
    }

    /** After dying. */
    public PvpStats withDeath(String currentName) {
        return new PvpStats(id, currentName, kills, deaths + 1, 0, bestStreak);
    }

    /** Kills per death, as Minecraft servers show it: kills when there are no deaths yet. */
    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /** What a leaderboard ranks by. */
    public enum Board {
        KILLS("kills"),
        DEATHS("deaths"),
        STREAK("streak"),
        BEST_STREAK("best_streak");

        private final String column;

        Board(String column) {
            this.column = column;
        }

        /** The column in the database, and the word in placeholders ({@code %pvp_top_best_streak_1_name%}). */
        public String column() {
            return column;
        }

        public int of(PvpStats stats) {
            return switch (this) {
                case KILLS -> stats.kills();
                case DEATHS -> stats.deaths();
                case STREAK -> stats.streak();
                case BEST_STREAK -> stats.bestStreak();
            };
        }
    }
}