package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import net.hollowcube.polar.PolarLoader;
import net.hollowcube.polar.PolarWorld;
import net.hollowcube.polar.PolarWriter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.anvil.AnvilLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads real Polar and Anvil maps through {@link WorldLoader}. This is the test that proves Polar 1.16.0
 * works with our Minestom version (see {@link LobbyPolarWorldAccess}).
 */
class WorldLoaderIntegrationTest {

    private static final Pos SPAWN = new Pos(0.5, 65, 0.5);
    private static final int MAP_RADIUS_CHUNKS = 2;
    private static final int PRELOAD_RADIUS = 1;

    @TempDir
    Path dir;

    @BeforeAll
    static void initMinestom() {
        if (MinecraftServer.process() == null) {
            MinecraftServer.init();
        }
    }

    @Test
    void loadsPolarMap() throws Exception {
        Path file = dir.resolve("lobby.polar");
        writePolarMap(file);

        LobbyWorld world = WorldLoader.load(settings(file, true), SPAWN);

        assertEquals(WorldFormat.POLAR, world.format());
        assertEquals(Block.GRASS_BLOCK, world.instance().getBlock(0, 64, 0));
        assertTrue(world.chunkCount() >= (2 * MAP_RADIUS_CHUNKS + 1) * (2 * MAP_RADIUS_CHUNKS + 1),
                "every chunk stored in the map must be preloaded");
    }

    @Test
    void convertsAnvilToPolarOnceThenLoadsPolar() throws Exception {
        Path anvil = dir.resolve("lobby");
        writeLegacyAnvilMap(anvil);

        LobbyWorld first = WorldLoader.load(settings(anvil, true), SPAWN);

        Path polar = dir.resolve("lobby.polar");
        assertEquals(WorldFormat.ANVIL, first.format());
        assertTrue(Files.isRegularFile(polar), "converted .polar file must be written");
        assertTrue(Files.isDirectory(anvil.resolve("region")), "original Anvil world must be kept");
        assertEquals(Block.GRASS_BLOCK, first.instance().getBlock(0, 64, 0));

        LobbyWorld second = WorldLoader.load(settings(anvil, true), SPAWN);

        assertEquals(WorldFormat.POLAR, second.format());
        assertEquals(Block.GRASS_BLOCK, second.instance().getBlock(0, 64, 0));
        assertFalse(second.lightComputed(), "light was saved during conversion, so it must not be computed again");
    }

    /**
     * Uses its own folder that is not deleted afterwards: the read-only Anvil loader keeps region files
     * open for the life of the server, and Windows cannot delete open files.
     */
    @Test
    void loadsAnvilWithoutConversion(@TempDir(cleanup = CleanupMode.NEVER) Path keptDir) throws Exception {
        Path anvil = keptDir.resolve("plain");
        writeLegacyAnvilMap(anvil);

        LobbyWorld world = WorldLoader.load(settings(anvil, false), SPAWN);

        assertEquals(WorldFormat.ANVIL, world.format());
        assertEquals(Block.GRASS_BLOCK, world.instance().getBlock(0, 64, 0));
        assertFalse(Files.exists(keptDir.resolve("plain.polar")));
    }

    @Test
    void missingMapGivesFlatPlatform() {
        LobbyWorld world = WorldLoader.load(settings(dir.resolve("missing.polar"), true), SPAWN);

        assertEquals(WorldFormat.FLAT_FALLBACK, world.format());
        assertEquals(Block.GRASS_BLOCK, world.instance().getBlock(0, 64, 0));
        assertEquals(Block.AIR, world.instance().getBlock(0, 65, 0));
        assertTrue(world.lightComputed());
    }

    @Test
    void brokenPolarFileGivesFlatPlatform() throws Exception {
        Path file = dir.resolve("broken.polar");
        Files.write(file, new byte[]{1, 2, 3, 4});

        LobbyWorld world = WorldLoader.load(settings(file, true), SPAWN);

        assertEquals(WorldFormat.FLAT_FALLBACK, world.format());
    }

    private static LobbyConfig.World settings(Path path, boolean convert) {
        return new LobbyConfig.World(path.toString(), convert, PRELOAD_RADIUS, 8, 6000, 0);
    }

    /** Builds a small map with the flat generator and saves it as Polar. */
    private static void writePolarMap(Path file) throws Exception {
        InstanceContainer source = MinecraftServer.getInstanceManager().createInstanceContainer();
        PolarLoader loader = new PolarLoader(new PolarWorld()).setWorldAccess(LobbyPolarWorldAccess.INSTANCE);
        source.setChunkLoader(loader);
        source.setGenerator(new FlatPlatformGenerator(SPAWN));
        loadMapChunks(source);
        loader.saveChunks(source.getChunks());
        Files.write(file, PolarWriter.write(loader.world()));
        MinecraftServer.getInstanceManager().unregisterInstance(source);
    }

    /** Builds a small map with the flat generator and saves it as a pre-26.1 Anvil world (region/ at the root). */
    @SuppressWarnings("removal")
    private static void writeLegacyAnvilMap(Path folder) throws Exception {
        Files.createDirectories(folder.resolve("region"));
        InstanceContainer source = MinecraftServer.getInstanceManager().createInstanceContainer();
        source.setChunkLoader(new AnvilLoader(folder));
        source.setGenerator(new FlatPlatformGenerator(SPAWN));
        loadMapChunks(source);
        source.saveChunksToStorage().join();
        MinecraftServer.getInstanceManager().unregisterInstance(source);
    }

    private static void loadMapChunks(InstanceContainer instance) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int x = -MAP_RADIUS_CHUNKS; x <= MAP_RADIUS_CHUNKS; x++) {
            for (int z = -MAP_RADIUS_CHUNKS; z <= MAP_RADIUS_CHUNKS; z++) {
                futures.add(instance.loadChunk(x, z));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }
}
