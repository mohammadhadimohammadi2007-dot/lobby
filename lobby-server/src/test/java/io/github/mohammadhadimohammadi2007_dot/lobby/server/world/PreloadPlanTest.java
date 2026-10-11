package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which chunks are loaded before the port opens, and reading an Anvil world's chunk list. */
class PreloadPlanTest {

    private static final Pos SPAWN = new Pos(8, 64, 8);

    /** A square map of {@code side} x {@code side} chunks starting at chunk 0, 0. */
    private static List<Long> square(int side) {
        List<Long> keys = new ArrayList<>();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                keys.add(CoordConversion.chunkIndex(x, z));
            }
        }
        return keys;
    }

    @Test
    void theWholeMapAndViewDistancePlusOneAroundItsEdges() {
        PreloadPlan plan = PreloadPlan.of(square(20), SPAWN, true, 20_000, 6, 8);

        assertTrue(plan.wholeMap());
        assertEquals(400, plan.storedChunks());
        // 20 chunks plus 9 on every side.
        assertEquals((20 + 18) * (20 + 18), plan.chunks().size());
        assertTrue(plan.chunks().contains(CoordConversion.chunkIndex(-9, -9)));
        assertFalse(plan.chunks().contains(CoordConversion.chunkIndex(-10, 0)));
        assertTrue(plan.chunks().contains(CoordConversion.chunkIndex(28, 28)));
    }

    @Test
    void aHugeMapFallsBackToTheAreaAroundSpawn() {
        PreloadPlan plan = PreloadPlan.of(square(200), SPAWN, true, 20_000, 6, 8);

        assertFalse(plan.wholeMap());
        assertEquals(19 * 19, plan.chunks().size(), "view-distance + 1 = 9 around spawn");
        assertTrue(plan.reason().contains("preload-max-chunks"), plan.reason());
    }

    @Test
    void turnedOffOrNoStoredChunksMeansTheSpawnArea() {
        assertEquals(15 * 15, PreloadPlan.of(square(20), SPAWN, false, 20_000, 7, 4).chunks().size(),
                "preload-radius 7 is bigger than view-distance + 1");
        PreloadPlan generated = PreloadPlan.of(List.of(), SPAWN, true, 20_000, 6, 8);
        assertFalse(generated.wholeMap());
        assertEquals(19 * 19, generated.chunks().size());
    }

    @TempDir
    Path regions;

    @Test
    void anvilRegionHeadersListTheSavedChunks() throws Exception {
        ByteBuffer header = ByteBuffer.allocate(8192);
        header.putInt(0, 2 << 8 | 1);                 // chunk (0, 0) of the region
        header.putInt((5 + 3 * 32) * 4, 3 << 8 | 1);  // chunk (5, 3)
        Files.write(regions.resolve("r.-1.2.mca"), header.array());
        Files.write(regions.resolve("r.0.0.mca"), new byte[100]); // too short: skipped
        Files.writeString(regions.resolve("notes.txt"), "not a region");

        List<Long> chunks = AnvilRegions.storedChunks(regions);

        assertEquals(Set.of(CoordConversion.chunkIndex(-32, 64), CoordConversion.chunkIndex(-32 + 5, 64 + 3)),
                Set.copyOf(chunks));
    }
}
