package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * {@code %pvp_...%}: a player's {@code kills}, {@code deaths}, {@code streak}, {@code best_streak}, {@code kdr},
 * {@code in_area}, {@code in_combat}, {@code combat_seconds}; {@code players} (fighting now); and leaderboards,
 * {@code top_<kills|deaths|streak|best_streak>_<place>_<name|value>}.
 */
public final class PvpPlaceholders implements PlaceholderNamespace {

    private static final Duration SHORT = Duration.ofSeconds(1);

    private final PvpService pvp;

    public PvpPlaceholders(PvpService pvp) {
        this.pvp = pvp;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            // Cached until the record changes (the stats service tells the placeholders).
            case "kills" -> Placeholder.player(p -> String.valueOf(pvp.stats().get(p.getUuid(), p.getUsername()).kills()));
            case "deaths" -> Placeholder.player(p -> String.valueOf(pvp.stats().get(p.getUuid(), p.getUsername()).deaths()));
            case "streak" -> Placeholder.player(p -> String.valueOf(pvp.stats().get(p.getUuid(), p.getUsername()).streak()));
            case "best_streak" -> Placeholder.player(p ->
                    String.valueOf(pvp.stats().get(p.getUuid(), p.getUsername()).bestStreak()));
            case "kdr" -> Placeholder.player(p ->
                    String.format(Locale.ROOT, "%.2f", pvp.stats().get(p.getUuid(), p.getUsername()).kdr()));
            case "in_area" -> Placeholder.player(SHORT, p -> pvp.inArea(p) ? "yes" : "no");
            case "in_combat" -> Placeholder.player(SHORT, p -> pvp.combatSecondsLeft(p) > 0 ? "yes" : "no");
            case "combat_seconds" -> Placeholder.player(SHORT, p -> String.valueOf(pvp.combatSecondsLeft(p)));
            case "players" -> Placeholder.global(SHORT, () -> String.valueOf(pvp.playersInArea()));
            default -> params.startsWith("top_") ? top(params.substring("top_".length())) : null;
        };
    }

    /** {@code best_streak_1_name}: board, place, and whether the name or the number is wanted. */
    private @Nullable Placeholder top(String rest) {
        boolean name;
        if (rest.endsWith("_name")) {
            name = true;
        } else if (rest.endsWith("_value")) {
            name = false;
        } else {
            return null;
        }
        String withoutPart = rest.substring(0, rest.lastIndexOf('_'));
        int lastUnderscore = withoutPart.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return null;
        }
        int place;
        try {
            place = Integer.parseInt(withoutPart.substring(lastUnderscore + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        String column = withoutPart.substring(0, lastUnderscore);
        PvpStats.Board board = null;
        for (PvpStats.Board candidate : PvpStats.Board.values()) {
            if (candidate.column().equals(column)) {
                board = candidate;
            }
        }
        if (board == null || place < 1) {
            return null;
        }
        PvpStats.Board chosen = board;
        // Global: the same for everyone, so a leaderboard hologram is built once for all players.
        return Placeholder.global(SHORT, () -> {
            PvpConfig config = pvp.config();
            if (place > config.leaderboardSize()) {
                return "";
            }
            List<PvpStats> entries = pvp.stats().top(chosen, config.leaderboardSize(), config.leaderboardRefreshMillis());
            if (place > entries.size()) {
                return name ? "---" : "0";
            }
            PvpStats entry = entries.get(place - 1);
            return name ? entry.name() : String.valueOf(chosen.of(entry));
        });
    }
}