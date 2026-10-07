package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;

/**
 * Generates a small square platform under the spawn point and nothing else.
 * Used when the configured map cannot be found, so the server still starts.
 */
final class FlatPlatformGenerator implements Generator {

    /** Platform size: blocks from the center to each edge. */
    static final int RADIUS = 16;

    private static final Block SURFACE = Block.GRASS_BLOCK;
    private static final Block BELOW = Block.DIRT;
    private static final int BELOW_DEPTH = 2;

    private final Point min;
    private final Point max;
    private final int surfaceY;

    /** @param center the spawn point; the platform's top surface is just below it */
    FlatPlatformGenerator(Point center) {
        this.surfaceY = center.blockY() - 1;
        this.min = new Vec(center.blockX() - RADIUS, surfaceY - BELOW_DEPTH, center.blockZ() - RADIUS);
        // Generator end points are exclusive.
        this.max = new Vec(center.blockX() + RADIUS + 1, surfaceY + 1, center.blockZ() + RADIUS + 1);
    }

    @Override
    public void generate(GenerationUnit unit) {
        Point start = unit.absoluteStart();
        Point end = unit.absoluteEnd();
        Vec from = new Vec(Math.max(start.x(), min.x()), Math.max(start.y(), min.y()), Math.max(start.z(), min.z()));
        Vec to = new Vec(Math.min(end.x(), max.x()), Math.min(end.y(), max.y()), Math.min(end.z(), max.z()));
        if (from.x() >= to.x() || from.y() >= to.y() || from.z() >= to.z()) {
            return;
        }
        unit.modifier().fill(from, to, BELOW);
        if (from.y() <= surfaceY && to.y() > surfaceY) {
            unit.modifier().fill(from.withY(surfaceY), to.withY(surfaceY + 1), SURFACE);
        }
    }
}
