package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Regions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The lobby's regions for an addon, and the addon's own: owned by {@code addon:<id>} in the lobby's
 * {@link RegionTracker}, so walking in and out fires the same region events as portals and jump pads.
 */
final class ApiRegions implements Regions {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private final String owner;
    private final RegionTracker tracker;
    // The lobby's own Region type, written out: inside this class "Region" is the API's Regions.Region.
    private final Map<String, io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region> own =
            new LinkedHashMap<>();

    ApiRegions(String addon, RegionTracker tracker) {
        this.owner = "addon:" + addon;
        this.tracker = tracker;
    }

    @Override
    public List<Regions.Region> at(Player player) {
        return at(player.getPosition());
    }

    @Override
    public List<Regions.Region> at(Point point) {
        return tracker.regionsAt(point).stream().map(ApiRegions::view).toList();
    }

    @Override
    public synchronized Regions.Region define(String name, Point corner1, Point corner2) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A region name of lowercase letters, digits, - and _: " + name);
        }
        var region = io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region.between(owner, name,
                corner1, corner2);
        own.put(name, region);
        tracker.setRegions(owner, new ArrayList<>(own.values()));
        return view(region);
    }

    @Override
    public synchronized boolean remove(String name) {
        if (own.remove(name) == null) {
            return false;
        }
        tracker.setRegions(owner, new ArrayList<>(own.values()));
        return true;
    }

    @Override
    public synchronized List<Regions.Region> own() {
        return own.values().stream().map(ApiRegions::view).toList();
    }

    /** Removes the addon's regions. */
    synchronized void cleanup() {
        if (!own.isEmpty()) {
            own.clear();
            tracker.setRegions(owner, List.of());
        }
    }

    private static Regions.Region view(io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region region) {
        return new Regions.Region(region.owner(), region.name(), region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ());
    }
}
