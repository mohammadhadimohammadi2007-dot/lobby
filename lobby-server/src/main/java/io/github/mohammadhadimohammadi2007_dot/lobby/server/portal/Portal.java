package io.github.mohammadhadimohammadi2007_dot.lobby.server.portal;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;

import java.util.List;
import java.util.function.Consumer;

/**
 * One portal as stored in {@code data/portals.yml}: a box of blocks that runs actions for every player
 * who walks in. It exists in every lobby instance, because they share one map.
 *
 * <p>Immutable: a change makes a new portal, so the movement code never sees half an edit.
 *
 * @param region     where it is; its owner is {@link #OWNER}
 * @param entries    the actions as written, kept so the file keeps the admin's own words
 * @param actions    the same, parsed
 * @param permission needed to use it, {@code ""} for everyone
 */
public record Portal(String name, Region region, List<Object> entries, ActionList actions, long cooldownMillis,
                     String permission) {

    /** Owner of portal regions in the region tracker. */
    public static final String OWNER = "portal";
    public static final long DEFAULT_COOLDOWN_MILLIS = 2000;
    /** Larger portals are almost always a mistake with the corners, and slow down region lookups. */
    public static final long MAX_VOLUME = 50_000;

    public Portal {
        entries = List.copyOf(entries);
    }

    /** A new portal with no actions yet. */
    public static Portal create(String name, Region region) {
        return new Portal(name, region, List.of(), ActionList.EMPTY, DEFAULT_COOLDOWN_MILLIS, "");
    }

    /** The same portal with other actions, parsed with its cooldown. */
    public Portal withActions(List<Object> newEntries, Consumer<String> warn) {
        return new Portal(name, region, newEntries,
                ActionParser.parseList(newEntries, cooldownMillis, "portals.yml: " + name, warn), cooldownMillis, permission);
    }

    public Portal withCooldown(long millis, Consumer<String> warn) {
        return new Portal(name, region, entries, actions, Math.max(0, millis), permission).withActions(entries, warn);
    }

    public Portal withPermission(String value) {
        return new Portal(name, region, entries, actions, cooldownMillis, value.strip());
    }

    public Portal withRegion(Region value) {
        return new Portal(name, value, entries, actions, cooldownMillis, permission);
    }
}
