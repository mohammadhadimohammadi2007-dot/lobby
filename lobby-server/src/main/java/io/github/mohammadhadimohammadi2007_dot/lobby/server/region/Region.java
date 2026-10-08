package io.github.mohammadhadimohammadi2007_dot.lobby.server.region;

import net.minestom.server.coordinate.Point;

/**
 * A box of blocks in the lobby map, corners included. The same region exists in every lobby instance,
 * because they all share one map.
 *
 * @param owner what made it, e.g. {@code portal} or {@code jump-pad}, so each feature can replace its own
 * @param name  unique within the owner
 */
public record Region(String owner, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Region {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min corner must not be above max corner");
        }
    }

    /** The region between two corners, in any order. */
    public static Region between(String owner, String name, Point a, Point b) {
        return new Region(owner, name,
                Math.min(a.blockX(), b.blockX()), Math.min(a.blockY(), b.blockY()), Math.min(a.blockZ(), b.blockZ()),
                Math.max(a.blockX(), b.blockX()), Math.max(a.blockY(), b.blockY()), Math.max(a.blockZ(), b.blockZ()));
    }

    /** True if the block at these coordinates is inside. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Number of blocks inside. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
