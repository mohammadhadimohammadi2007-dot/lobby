package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Pos;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which chunks are loaded before players can join. A chunk loaded later is made and lit on the tick thread
 * the moment a player first comes near it, so by default everything a player can ever be sent is loaded:
 * every chunk stored in the map, plus {@code view-distance + 1} around them (what a player standing at the
 * edge is sent; Minestom sends one ring more than the view distance), plus the same around spawn.
 *
 * <p>For a map too big for that ({@code preload-max-chunks}), only {@code preload-radius} around spawn is
 * loaded, as before.
 *
 * @param chunks       the chunk keys to load
 * @param storedChunks how many of them are stored in the map file
 * @param wholeMap     true if the whole map is loaded, false if only the area around spawn
 * @param reason       why only the spawn area is loaded, or empty
 */
public record PreloadPlan(Set<Long> chunks, int storedChunks, boolean wholeMap, String reason) {

    public PreloadPlan {
        chunks = Set.copyOf(chunks);
    }

    /**
     * @param stored        chunk keys stored in the map (empty for a generated map)
     * @param wholeMap      {@code world.preload-whole-map}
     * @param maxChunks     {@code world.preload-max-chunks}
     * @param preloadRadius {@code world.preload-radius}
     * @param viewDistance  {@code world.view-distance}
     */
    public static PreloadPlan of(Collection<Long> stored, Pos spawn, boolean wholeMap, int maxChunks, int preloadRadius,
                                 int viewDistance) {
        int ring = viewDistance + 1;
        Set<Long> aroundSpawn = around(spawn.chunkX(), spawn.chunkZ(), Math.max(preloadRadius, ring));
        if (!wholeMap) {
            return new PreloadPlan(aroundSpawn, 0, false, "world.preload-whole-map is false");
        }
        if (stored.isEmpty()) {
            return new PreloadPlan(aroundSpawn, 0, false, "the map has no stored chunks");
        }
        Set<Long> storedSet = new HashSet<>(stored);
        Set<Long> chunks = new LinkedHashSet<>(stored);
        for (long key : storedSet) {
            int x = CoordConversion.chunkIndexGetX(key);
            int z = CoordConversion.chunkIndexGetZ(key);
            // Only chunks at the edge add anything; inner chunks' rings are already in the map.
            if (storedSet.contains(CoordConversion.chunkIndex(x + 1, z)) && storedSet.contains(CoordConversion.chunkIndex(x - 1, z))
                    && storedSet.contains(CoordConversion.chunkIndex(x, z + 1)) && storedSet.contains(CoordConversion.chunkIndex(x, z - 1))) {
                continue;
            }
            chunks.addAll(around(x, z, ring));
            if (chunks.size() > maxChunks) {
                break;
            }
        }
        chunks.addAll(aroundSpawn);
        if (chunks.size() > maxChunks) {
            return new PreloadPlan(aroundSpawn, 0, false, "the map with its edges is more than world.preload-max-chunks ("
                    + maxChunks + ") chunks");
        }
        return new PreloadPlan(chunks, storedSet.size(), true, "");
    }

    private static Set<Long> around(int centerX, int centerZ, int radius) {
        Set<Long> keys = new LinkedHashSet<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                keys.add(CoordConversion.chunkIndex(x, z));
            }
        }
        return keys;
    }
}
