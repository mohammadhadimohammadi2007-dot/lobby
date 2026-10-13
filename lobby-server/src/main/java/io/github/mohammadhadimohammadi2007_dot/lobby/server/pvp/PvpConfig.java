package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigReader;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * pvp.yml: how fights in the PvP area work. Where the area is, the spawn and the kit are in data/pvp.yml.
 *
 * @param combatTagMillis       how long a hit keeps players in combat, 0 = no combat tag
 * @param combatBlockedCommands command names blocked while in combat, lower case
 * @param areaBlockedCommands   command names blocked inside the area
 * @param maxReach              hits from further away (blocks) are ignored
 */
public record PvpConfig(boolean enabled, boolean sameVersionOnly, long combatTagMillis,
                        Set<String> combatBlockedCommands, Set<String> areaBlockedCommands,
                        boolean combatLogCountsAsDeath, boolean sweepAttacks, double maxReach,
                        ActionList killActions, ActionList deathActions, int leaderboardSize,
                        long leaderboardRefreshMillis) {

    private static final int MAX_TAG_SECONDS = 120;
    private static final int MAX_LEADERBOARD = 100;

    public PvpConfig {
        combatBlockedCommands = Set.copyOf(combatBlockedCommands);
        areaBlockedCommands = Set.copyOf(areaBlockedCommands);
    }

    /** Reads pvp.yml. */
    public static PvpConfig read(ConfigReader reader) {
        return new PvpConfig(
                reader.bool("enabled", true),
                reader.bool("same-version-only", false),
                reader.integer("combat-tag-seconds", 0, MAX_TAG_SECONDS, 10) * 1000L,
                commands(reader.stringList("combat-blocked-commands", List.of("spawn", "lobbies", "fly"))),
                commands(reader.stringList("area-blocked-commands", List.of("fly"))),
                reader.bool("combat-log-counts-as-death", true),
                reader.bool("sweep-attacks", true),
                reader.decimal("max-reach", 3, 32),
                actions(reader, "kill-actions"),
                actions(reader, "death-actions"),
                reader.integer("leaderboard-size", 1, MAX_LEADERBOARD, 10),
                reader.integer("leaderboard-refresh", 5, 3600, 60) * 1000L);
    }

    private static Set<String> commands(List<String> names) {
        return names.stream().map(name -> name.strip().toLowerCase(Locale.ROOT))
                .map(name -> name.startsWith("/") ? name.substring(1) : name)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ActionList actions(ConfigReader reader, String path) {
        return ActionParser.parseList(reader.lineList(path), 0, reader.fileName() + ": " + path, reader::addWarning);
    }
}