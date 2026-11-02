package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Areas of the lobby map: the lobby's own (portals, jump pads, the PvP area...) and the addon's. A player walking in
 * or out of any of them fires {@code RegionEnterEvent} and {@code RegionLeaveEvent}. Every lobby instance shares the
 * map, so a region exists in all of them. Any thread.
 */
@NonExtendable
public interface Regions {

    /**
     * A box of blocks, corners included.
     *
     * @param owner what it belongs to: {@code portal}, {@code jump-pad}, {@code pvp}... or {@code addon:<id>} for an
     *              addon's regions; the same as {@code RegionEnterEvent.owner()}
     * @param name  unique within its owner
     */
    record Region(String owner, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public Region {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
        }

        /** True if the block at the point is inside. */
        public boolean contains(Point point) {
            return point.blockX() >= minX && point.blockX() <= maxX && point.blockY() >= minY
                    && point.blockY() <= maxY && point.blockZ() >= minZ && point.blockZ() <= maxZ;
        }
    }

    /** The regions the player stands in. */
    List<Region> at(Player player);

    /** The regions around a point. */
    List<Region> at(Point point);

    /**
     * Adds or replaces one of this addon's regions, between two corners in any order. Its owner is
     * {@code addon:<id>}. The addon's regions are removed when it is disabled.
     *
     * @param name lowercase letters, digits, {@code -} and {@code _}
     * @throws IllegalArgumentException if the name is invalid
     */
    Region define(String name, Point corner1, Point corner2);

    /** Removes one of this addon's regions; false if it has none with that name. */
    boolean remove(String name);

    /** This addon's regions. */
    List<Region> own();
}
