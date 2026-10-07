package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSourceTest {

    @TempDir
    Path dir;

    @Test
    void polarFile() throws IOException {
        Path polar = Files.createFile(dir.resolve("lobby.polar"));

        WorldSource source = WorldSource.detect(polar, true);

        assertEquals(WorldFormat.POLAR, source.format());
        assertEquals(polar, source.path());
    }

    @Test
    void polarExtensionIsCaseInsensitive() throws IOException {
        Path polar = Files.createFile(dir.resolve("Lobby.POLAR"));

        assertEquals(WorldFormat.POLAR, WorldSource.detect(polar, false).format());
    }

    @Test
    void anvilWithoutConversion() throws IOException {
        Path world = anvilWorld("lobby");

        WorldSource source = WorldSource.detect(world, false);

        assertEquals(WorldFormat.ANVIL, source.format());
        assertNull(source.convertTarget());
    }

    @Test
    void modernAnvilLayoutIsDetected() throws IOException {
        Path world = dir.resolve("new-world");
        Files.createDirectories(world.resolve("dimensions/minecraft/overworld/region"));

        WorldSource source = WorldSource.detect(world, false);

        assertEquals(WorldFormat.ANVIL, source.format());
        assertTrue(WorldSource.isModernLayout(world));
        assertEquals(world.resolve("dimensions/minecraft/overworld"), WorldSource.regionParent(world));
    }

    @Test
    void legacyAnvilLayoutRegionParentIsWorld() throws IOException {
        Path world = anvilWorld("old-world");

        assertEquals(world, WorldSource.regionParent(world));
        assertEquals(false, WorldSource.isModernLayout(world));
    }

    @Test
    void anvilWithConversionTargetsSiblingPolarFile() throws IOException {
        Path world = anvilWorld("lobby");

        WorldSource source = WorldSource.detect(world, true);

        assertEquals(WorldFormat.ANVIL, source.format());
        assertEquals(dir.resolve("lobby.polar").toAbsolutePath().normalize(), source.convertTarget());
    }

    @Test
    void alreadyConvertedAnvilLoadsPolar() throws IOException {
        Path world = anvilWorld("lobby");
        Files.createFile(dir.resolve("lobby.polar"));

        WorldSource source = WorldSource.detect(world, true);

        assertEquals(WorldFormat.POLAR, source.format());
        assertEquals(dir.resolve("lobby.polar").toAbsolutePath().normalize(), source.path());
    }

    @Test
    void missingPolarFileFindsAnvilFolderWithSameName() throws IOException {
        Path world = anvilWorld("lobby");

        WorldSource source = WorldSource.detect(dir.resolve("lobby.polar"), true);

        assertEquals(WorldFormat.ANVIL, source.format());
        assertEquals(world, source.path());
    }

    @Test
    void missingPathFallsBackToFlat() {
        WorldSource source = WorldSource.detect(dir.resolve("nothing.polar"), true);

        assertEquals(WorldFormat.FLAT_FALLBACK, source.format());
        assertTrue(source.problem().contains("does not exist"));
    }

    @Test
    void folderWithoutRegionFallsBackWithHint() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("saves"));

        WorldSource source = WorldSource.detect(folder, true);

        assertEquals(WorldFormat.FLAT_FALLBACK, source.format());
        assertNotNull(source.problem());
        assertTrue(source.problem().contains("region"));
    }

    @Test
    void otherFileFallsBack() throws IOException {
        Path file = Files.createFile(dir.resolve("lobby.zip"));

        assertEquals(WorldFormat.FLAT_FALLBACK, WorldSource.detect(file, true).format());
    }

    private Path anvilWorld(String name) throws IOException {
        Path world = dir.resolve(name);
        Files.createDirectories(world.resolve("region"));
        return world;
    }
}
