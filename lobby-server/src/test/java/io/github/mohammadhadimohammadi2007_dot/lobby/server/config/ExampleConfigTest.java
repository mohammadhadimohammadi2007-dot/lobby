package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lobby files of examples/network load without a warning and mean what their comments say. */
class ExampleConfigTest {

    private static final Path EXAMPLE = Path.of("..", "examples", "network", "lobby");

    @TempDir
    Path dir;

    @Test
    void theNetworkExampleLoadsCleanly() throws Exception {
        Files.copy(EXAMPLE.resolve("config.yml"), dir.resolve(ConfigManager.CONFIG_FILE));
        Files.copy(EXAMPLE.resolve("integrations.yml"), dir.resolve(ConfigManager.INTEGRATIONS_FILE));

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        LobbyConfig config = snapshot.config();
        assertEquals(ConnectionMode.VELOCITY, config.connection().mode());
        assertEquals("lobby-1", config.server().name());
        assertEquals(25566, config.server().port());
        assertTrue(config.namesTrusted());
        assertTrue(snapshot.integrations().luckPerms().enabled());
        assertTrue(snapshot.integrations().bridge().enabled());
        // The example only lists what to change: the rest is filled in from the defaults, with a note each,
        // but nothing may be invalid.
        List<String> invalid = snapshot.warnings().stream().filter(w -> !w.contains("is missing")).toList();
        assertEquals(List.of(), invalid);
    }
}
