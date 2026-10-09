package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.color.TeamColor;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Every player's team, which decides two things at once: where they are in the tab list, and the prefix
 * and suffix of the nametag above their head. Teams are made by {@link TeamManager}, which only sends
 * what changed.
 *
 * <p>Sorting: by the weight of the player's LuckPerms group, highest first. A group without a weight is
 * placed by {@code tab.group-order}. Weights are turned into places 0-98 by rank among the players
 * online, so any weights work, not only small ones.
 */
final class PlayerTeams {

    private final TeamManager teams;
    private final PermissionService permissions;

    PlayerTeams(TeamManager teams, PermissionService permissions) {
        this.teams = teams;
        this.permissions = permissions;
    }

    void refresh(TabConfig config, Collection<Player> online, TextCache text) {
        if (!config.enabled() && !config.nametags().enabled()) {
            return;
        }
        Map<Player, Integer> sortValues = new HashMap<>();
        TreeSet<Integer> distinct = new TreeSet<>();
        for (Player player : online) {
            int value = sortValue(permissions.meta(player), config.groupOrder());
            sortValues.put(player, value);
            distinct.add(value);
        }
        // Highest value first: its place is how many distinct values are above it.
        List<Integer> descending = List.copyOf(distinct.descendingSet());
        TabConfig.Nametags nametags = config.nametags();
        for (Player player : online) {
            int order = Math.min(TeamManager.MAX_ORDER - 1, descending.indexOf(sortValues.get(player)));
            if (!nametags.enabled()) {
                teams.setPlayer(player, order, Component.empty(), Component.empty(), TeamColor.WHITE);
                continue;
            }
            Component prefix = text.about(nametags.prefix().modern(), player, false);
            Component suffix = text.about(nametags.suffix().modern(), player, false);
            Component legacyPrefix = nametags.prefix().legacy() == null ? null
                    : text.about(nametags.prefix().legacy(), player, true);
            Component legacySuffix = nametags.suffix().legacy() == null ? null
                    : text.about(nametags.suffix().legacy(), player, true);
            TeamColor color = nametags.nameColor() != null ? nametags.nameColor() : lastColor(prefix);
            teams.setPlayer(player, order, prefix, suffix, legacyPrefix, legacySuffix, color);
        }
    }

    /**
     * Higher sorts first: the group's weight, or for a group without one, its place in {@code groupOrder}
     * counted from the end (so the first listed is highest). Unknown groups without a weight get 0.
     */
    static int sortValue(PlayerMeta meta, List<String> groupOrder) {
        if (meta.weight() != 0) {
            return meta.weight();
        }
        int index = groupOrder.indexOf(meta.primaryGroup().toLowerCase(Locale.ROOT));
        return index < 0 ? 0 : groupOrder.size() - index;
    }

    /** The colour the prefix ends in, as one of the 16 team colours; white if it has none. */
    static TeamColor lastColor(Component prefix) {
        TextColor last = last(prefix, null);
        if (last == null) {
            return TeamColor.WHITE;
        }
        NamedTextColor named = last instanceof NamedTextColor exact ? exact : NamedTextColor.nearestTo(last);
        TeamColor color = TabConfig.teamColor(NamedTextColor.NAMES.key(named));
        return color == null ? TeamColor.WHITE : color;
    }

    /** The colour of the last piece of text that has one, following inherited colours. */
    private static @Nullable TextColor last(Component component, @Nullable TextColor inherited) {
        TextColor own = component.color() != null ? component.color() : inherited;
        TextColor result = component instanceof TextComponent text && !text.content().isBlank() ? own : null;
        for (Component child : component.children()) {
            TextColor found = last(child, own);
            if (found != null) {
                result = found;
            }
        }
        return result;
    }
}
