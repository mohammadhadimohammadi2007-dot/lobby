package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlSectionEditorTest {

    private static Map<String, String> spawn(String x, String y) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("x", x);
        values.put("y", y);
        return values;
    }

    @Test
    void replacesValuesAndKeepsComments() {
        String yaml = """
                # top comment
                spawn:
                  # where players appear
                  x: 0.5   # east-west
                  y: 65
                  z: 0.5

                protection:
                  x: 1
                """;

        String result = YamlSectionEditor.updateText(yaml, "spawn", spawn("10.25", "70"));

        assertEquals("""
                # top comment
                spawn:
                  # where players appear
                  x: 10.25   # east-west
                  y: 70
                  z: 0.5

                protection:
                  x: 1
                """, result);
    }

    @Test
    void addsMissingKeysInsideSection() {
        String yaml = "spawn:\n    x: 1\nother: true\n";

        String result = YamlSectionEditor.updateText(yaml, "spawn", spawn("2", "3"));

        assertEquals("spawn:\n    x: 2\n    y: 3\nother: true\n", result);
    }

    @Test
    void appendsMissingSection() {
        String result = YamlSectionEditor.updateText("mode: standalone", "spawn", spawn("1", "2"));

        assertEquals("mode: standalone\nspawn:\n  x: 1\n  y: 2\n", result);
    }

    @Test
    void keepsWindowsLineEndings() {
        String result = YamlSectionEditor.updateText("spawn:\r\n  x: 1\r\n  y: 2\r\n", "spawn", spawn("5", "6"));

        assertEquals("spawn:\r\n  x: 5\r\n  y: 6\r\n", result);
    }

    @Test
    void updatedDefaultConfigStillLoads(@TempDir Path dir) throws Exception {
        new ConfigManager(dir).load();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("x", "100.5");
        values.put("y", "80");
        values.put("z", "-20.5");
        values.put("yaw", "90.0");
        values.put("pitch", "10.0");

        YamlSectionEditor.update(dir.resolve("config.yml"), "spawn", values);

        ConfigSnapshot snapshot = new ConfigManager(dir).load();
        assertEquals(new LobbyConfig.SpawnPoint(100.5, 80, -20.5, 90f, 10f), snapshot.config().spawn());
        assertTrue(Files.readString(dir.resolve("config.yml")).contains("# Where players appear"));
    }
}
