package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.Nullable;

/**
 * The PvP area of data/pvp.yml.
 *
 * @param region where fighting is allowed
 * @param spawn  where players come back after dying, or {@code null} for the middle of the area's floor
 * @param kit    what players get when they walk in
 */
public record PvpArena(Region region, @Nullable Pos spawn, Kit kit) {

    /** The region owner, for {@link io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker}. */
    public static final String OWNER = "pvp";
    /** The name of the area (there is one). */
    public static final String NAME = "area";
    /** The biggest area, so a typo in the corners cannot cover the whole map. */
    public static final long MAX_VOLUME = 2_000_000;

    /** Where a player respawns: the spawn, or the middle of the area's floor. */
    public Pos respawn() {
        if (spawn != null) {
            return spawn;
        }
        return new Pos((region.minX() + region.maxX()) / 2.0 + 0.5, region.minY(), (region.minZ() + region.maxZ()) / 2.0 + 0.5);
    }

    public PvpArena withSpawn(Pos value) {
        return new PvpArena(region, value, kit);
    }

    public PvpArena withKit(Kit value) {
        return new PvpArena(region, spawn, value);
    }

    public PvpArena withRegion(Region value) {
        return new PvpArena(value, spawn, kit);
    }
}