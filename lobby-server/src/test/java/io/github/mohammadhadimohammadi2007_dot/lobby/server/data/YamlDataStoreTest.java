package io.github.mohammadhadimohammadi2007_dot.lobby.server.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDataStoreTest {

    record Point(String world, int x) {
    }

    private static final DataCodec<Point> CODEC = new DataCodec<>() {
        @Override
        public Point read(ConfigurationNode node) throws DataException {
            if (node.node("x").virtual() || !(node.node("x").raw() instanceof Number number)) {
                throw new DataException("'x' must be a number");
            }
            return new Point(node.node("world").getString("lobby"), number.intValue());
        }

        @Override
        public void write(Point value, ConfigurationNode node) throws SerializationException {
            node.node("world").set(value.world());
            node.node("x").set(value.x());
        }
    };

    @TempDir
    Path dir;

    private YamlDataStore<Point> store() {
        return new YamlDataStore<>(dir.resolve("data").resolve("points.yml"), "Points. Edit with /point.", CODEC);
    }

    @Test
    void missingFileIsEmpty() {
        assertEquals(Map.of(), store().load());
    }

    @Test
    void saveAndLoadKeepOrder() {
        YamlDataStore<Point> store = store();
        Map<String, Point> points = new LinkedHashMap<>();
        points.put("zeta", new Point("lobby", 1));
        points.put("alpha", new Point("lobby", 2));
        store.save(points);
        store.flush();

        Map<String, Point> loaded = store().load();
        assertEquals(points, loaded);
        assertEquals(List.of("zeta", "alpha"), List.copyOf(loaded.keySet()));
    }

    @Test
    void brokenEntryIsSkippedAndKept() throws IOException {
        Path file = dir.resolve("points.yml");
        Files.writeString(file, """
                good:
                  x: 1
                bad:
                  x: "not a number"
                  note: hand written
                """);
        YamlDataStore<Point> store = new YamlDataStore<>(file, "Points", CODEC);

        Map<String, Point> loaded = store.load();
        assertEquals(Map.of("good", new Point("lobby", 1)), loaded);
        assertEquals(Set.of("bad"), store.brokenEntries());

        store.save(Map.of("good", new Point("lobby", 5), "new", new Point("hub", 7)));
        store.flush();
        String saved = Files.readString(file);
        assertTrue(saved.contains("note: hand written"), saved);
        assertTrue(saved.startsWith("# Points"), saved);
        assertEquals(Map.of("good", new Point("lobby", 5), "new", new Point("hub", 7)),
                new YamlDataStore<>(file, "Points", CODEC).load());
    }

    @Test
    void invalidYamlIsBackedUpBeforeSaving() throws IOException {
        Path file = dir.resolve("points.yml");
        Files.writeString(file, "good:\n  x: [1\n");
        YamlDataStore<Point> store = new YamlDataStore<>(file, "Points", CODEC);

        assertEquals(Map.of(), store.load());
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files.filter(path -> path.getFileName().toString().startsWith("points.broken-")).toList();
            assertEquals(1, backups.size());
            assertEquals("good:\n  x: [1\n", Files.readString(backups.getFirst()));
        }
    }

    @Test
    void quickSavesEndWithTheLastOne() {
        YamlDataStore<Point> store = store();
        for (int i = 0; i < 50; i++) {
            store.save(Map.of("p", new Point("lobby", i)));
        }
        store.close();

        assertEquals(Map.of("p", new Point("lobby", 49)), store().load());
    }
}
