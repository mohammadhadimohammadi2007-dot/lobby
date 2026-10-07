package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import net.hollowcube.polar.AnvilPolar;
import net.hollowcube.polar.PolarChunk;
import net.hollowcube.polar.PolarLoader;
import net.hollowcube.polar.PolarWorld;
import net.hollowcube.polar.PolarWriter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.world.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the lobby map into memory before players can join.
 *
 * <p>Never throws because of a bad map: if the map is missing or cannot be read, a small flat
 * platform is generated at spawn instead and a clear warning is logged.
 */
public final class WorldLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldLoader.class);

    /** Old 1.8 clients only see blocks from Y 0 to Y 255, which is sections 0 to 15. */
    private static final int LEGACY_MIN_SECTION = 0;
    private static final int LEGACY_MAX_SECTION = 15;
    private static final int SECTION_SIZE = 16;
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private WorldLoader() {
    }

    /**
     * Loads the map described by {@code settings}. Blocks until every chunk is loaded and lit.
     * Call this before the server starts accepting players.
     */
    public static LobbyWorld load(LobbyConfig.World settings, Pos spawn) {
        long startNanos = System.nanoTime();
        long memoryBefore = usedMemory();

        WorldSource source = WorldSource.detect(Path.of(settings.path()), settings.convertAnvilToPolar());
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);

        Loaded loaded;
        try {
            loaded = switch (source.format()) {
                case POLAR -> loadPolar(instance, source.path());
                case ANVIL -> source.convertTarget() != null
                        ? convertAnvil(instance, source.path(), source.convertTarget())
                        : loadAnvil(instance, source.path());
                case FLAT_FALLBACK -> {
                    LOGGER.warn("No map loaded: {}.", source.problem());
                    yield flatFallback(instance, spawn);
                }
            };
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not load the map from '{}': {}", source.path(), e.getMessage(), e);
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
            instance = MinecraftServer.getInstanceManager().createInstanceContainer();
            instance.setChunkSupplier(LightingChunk::new);
            loaded = flatFallback(instance, spawn);
        }

        Set<Long> chunks = new LinkedHashSet<>(loaded.mapChunks());
        chunks.addAll(chunksAround(spawn, settings.preloadRadius()));
        preload(instance, chunks);

        boolean lightComputed = false;
        if (needsLighting(instance)) {
            LightingChunk.relight(instance, instance.getChunks());
            lightComputed = true;
        }
        if (loaded.afterLoad() != null) {
            loaded.afterLoad().run();
        }

        warnOutsideLegacyHeight(instance);

        long loadMillis = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
        long memoryMb = Math.max(0, usedMemory() - memoryBefore) / BYTES_PER_MB;
        LobbyWorld world = new LobbyWorld(instance, loaded.name(), loaded.format(), instance.getChunks().size(),
                loadMillis, memoryMb, lightComputed);
        LOGGER.info("Map '{}' ready: {} ({} chunks, loaded in {} ms, about {} MB of memory{})",
                world.name(), world.format().name().toLowerCase(), world.chunkCount(), world.loadMillis(),
                world.memoryMb(), lightComputed ? ", lighting computed" : "");
        return world;
    }

    private static Loaded loadPolar(InstanceContainer instance, Path file) throws IOException {
        LOGGER.info("Loading Polar map {}", file);
        PolarLoader loader = new PolarLoader(file).setWorldAccess(LobbyPolarWorldAccess.INSTANCE);
        instance.setChunkLoader(new ReadOnlyChunkLoader(loader));
        return new Loaded(fileName(file), WorldFormat.POLAR, chunkKeys(loader.world()), null);
    }

    private static Loaded loadAnvil(InstanceContainer instance, Path folder) {
        LOGGER.info("Loading Anvil map {} (only chunks near spawn are preloaded;"
                + " turn on convert-anvil-to-polar to load the whole map up front)", folder);
        instance.setChunkLoader(new ReadOnlyChunkLoader(anvilLoader(folder)));
        return new Loaded(fileName(folder), WorldFormat.ANVIL, List.of(), null);
    }

    /**
     * Converts an Anvil world to Polar. The new file is written only after the map is fully loaded and lit,
     * so the saved file already contains lighting and later starts are fast.
     */
    private static Loaded convertAnvil(InstanceContainer instance, Path folder, Path target) throws IOException {
        LOGGER.info("Converting Anvil map {} to Polar (one time only, the original folder is kept)...", folder);
        PolarWorld polarWorld = AnvilPolar.anvilToPolar(WorldSource.regionParent(folder));
        // No save path: Polar only updates the world in memory and we write the file ourselves, safely.
        PolarLoader loader = new PolarLoader(polarWorld).setWorldAccess(LobbyPolarWorldAccess.INSTANCE);
        instance.setChunkLoader(new ReadOnlyChunkLoader(loader));
        Runnable writePolarFile = () -> {
            try {
                loader.saveChunks(instance.getChunks());
                writeAtomically(target, PolarWriter.write(loader.world()));
                LOGGER.info("Saved converted map to {}. Future starts load this file.", target);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Converted the map but could not save {}: {}. The map still works,"
                        + " but conversion will run again next start.", target, e.getMessage(), e);
            }
        };
        return new Loaded(fileName(folder), WorldFormat.ANVIL, chunkKeys(polarWorld), writePolarFile);
    }

    /** Minestom's Anvil loader for either folder layout. */
    @SuppressWarnings("removal")
    private static AnvilLoader anvilLoader(Path folder) {
        if (WorldSource.isModernLayout(folder)) {
            return new AnvilLoader(folder, DimensionType.OVERWORLD.key());
        }
        // Worlds saved before Minecraft 26.1 keep region/ in the world folder. Minestom marks this
        // constructor for removal; if it disappears, convert old maps to Polar instead.
        return new AnvilLoader(folder);
    }

    private static Loaded flatFallback(InstanceContainer instance, Pos spawn) {
        LOGGER.warn("Generating a small flat platform at spawn so the server can still start."
                + " Put your map at the path set in config.yml (world.path) and restart.");
        instance.setGenerator(new FlatPlatformGenerator(spawn));
        return new Loaded("flat platform", WorldFormat.FLAT_FALLBACK, List.of(), null);
    }

    private static void preload(InstanceContainer instance, Set<Long> chunkKeys) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>(chunkKeys.size());
        for (long key : chunkKeys) {
            futures.add(instance.loadChunk(CoordConversion.chunkIndexGetX(key), CoordConversion.chunkIndexGetZ(key)));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    /** True if any chunk that has blocks has no stored sky light. */
    private static boolean needsLighting(InstanceContainer instance) {
        for (Chunk chunk : instance.getChunks()) {
            for (Section section : chunk.getSections()) {
                if (section.blockPalette().count() == 0) {
                    continue;
                }
                byte[] skyLight = section.skyLight().array();
                if (skyLight == null || skyLight.length == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void warnOutsideLegacyHeight(InstanceContainer instance) {
        int chunksOutside = 0;
        for (Chunk chunk : instance.getChunks()) {
            for (int sectionY = chunk.getMinSection(); sectionY < chunk.getMaxSection(); sectionY++) {
                boolean outside = sectionY < LEGACY_MIN_SECTION || sectionY > LEGACY_MAX_SECTION;
                if (outside && chunk.getSection(sectionY).blockPalette().count() > 0) {
                    chunksOutside++;
                    break;
                }
            }
        }
        if (chunksOutside > 0) {
            LOGGER.warn("{} chunk(s) have blocks below Y {} or above Y {}. Players on 1.8 to 1.17 clients"
                            + " will not see those blocks. Keep the lobby between Y 0 and Y 255.",
                    chunksOutside, LEGACY_MIN_SECTION * SECTION_SIZE, (LEGACY_MAX_SECTION + 1) * SECTION_SIZE - 1);
        }
    }

    private static List<Long> chunkKeys(PolarWorld world) {
        List<Long> keys = new ArrayList<>();
        for (PolarChunk chunk : world.chunks()) {
            keys.add(CoordConversion.chunkIndex(chunk.x(), chunk.z()));
        }
        return keys;
    }

    private static List<Long> chunksAround(Pos center, int radius) {
        int centerX = center.chunkX();
        int centerZ = center.chunkZ();
        List<Long> keys = new ArrayList<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                keys.add(CoordConversion.chunkIndex(x, z));
            }
        }
        return keys;
    }

    private static void writeAtomically(Path target, byte[] data) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, data);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fileName(Path path) {
        Path name = path.toAbsolutePath().normalize().getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /** Heap in use after a garbage collection. Only called twice at startup, so the GC cost is fine. */
    private static long usedMemory() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * What a format-specific loader produced.
     *
     * @param mapChunks chunks stored in the map file (all of them are preloaded)
     * @param afterLoad optional step after every chunk is loaded and lit (used to save a converted map)
     */
    private record Loaded(String name, WorldFormat format, List<Long> mapChunks, Runnable afterLoad) {
    }
}
