package io.github.mohammadhadimohammadi2007_dot.lobby.server.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the regions at a block quickly: regions are filed under every chunk column they touch, so a lookup
 * only checks the few regions of one chunk. Very large regions are checked directly instead of being filed
 * under thousands of chunks. Immutable; rebuilt when regions change.
 */
public final class RegionIndex {

    public static final RegionIndex EMPTY = new RegionIndex(List.of());
    /** Regions touching more chunks than this are kept in a short list that is always checked. */
    private static final int MAX_INDEXED_CHUNKS = 1024;
    private static final int CHUNK_SHIFT = 4;

    private final List<Region> all;
    private final Map<Long, List<Region>> byChunk = new HashMap<>();
    private final List<Region> large = new ArrayList<>();

    public RegionIndex(List<Region> regions) {
        this.all = List.copyOf(regions);
        for (Region region : all) {
            int minChunkX = region.minX() >> CHUNK_SHIFT;
            int maxChunkX = region.maxX() >> CHUNK_SHIFT;
            int minChunkZ = region.minZ() >> CHUNK_SHIFT;
            int maxChunkZ = region.maxZ() >> CHUNK_SHIFT;
            long chunks = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
            if (chunks > MAX_INDEXED_CHUNKS) {
                large.add(region);
                continue;
            }
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    byChunk.computeIfAbsent(key(cx, cz), ignored -> new ArrayList<>(2)).add(region);
                }
            }
        }
    }

    public List<Region> all() {
        return all;
    }

    /**
     * Regions containing the block. Returns an empty immutable list when there are none (the usual case),
     * so most lookups allocate nothing.
     */
    public List<Region> at(int x, int y, int z) {
        List<Region> candidates = byChunk.get(key(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT));
        List<Region> found = null;
        if (candidates != null) {
            for (Region region : candidates) {
                if (region.contains(x, y, z)) {
                    found = add(found, region);
                }
            }
        }
        for (Region region : large) {
            if (region.contains(x, y, z)) {
                found = add(found, region);
            }
        }
        return found == null ? List.of() : found;
    }

    private static List<Region> add(List<Region> list, Region region) {
        List<Region> result = list == null ? new ArrayList<>(2) : list;
        result.add(region);
        return result;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
