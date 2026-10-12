package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bridge config of examples/network reads as documented. */
class ExampleConfigTest {

    @TempDir
    Path dir;

    @Test
    void theNetworkExampleReads() throws Exception {
        Files.copy(Path.of("..", "examples", "network", "velocity", "plugins", "lobby-bridge", "config.toml"),
                dir.resolve("config.toml"));

        BridgeConfig config = BridgeConfig.load(dir, NOPLogger.NOP_LOGGER);

        assertEquals(Set.of("lobby-1", "lobby-2"), config.lobbyServers());
        assertEquals(List.of("bedwars-1", "bedwars-2"), config.groups().get("bedwars"));
        assertEquals(List.of("lobby-1", "lobby-2"), config.groups().get("lobbies"), "added automatically");
        assertEquals(Map.of("bedwars", ServerPicker.Strategy.FILL_FIRST, "skywars", ServerPicker.Strategy.LEAST_PLAYERS),
                config.strategies());
        assertTrue(config.playerCommands());
    }

    @Test
    void theBundledDefaultReadsAndAllowsPlayerCommands() throws Exception {
        BridgeConfig config = BridgeConfig.load(dir, NOPLogger.NOP_LOGGER);

        assertTrue(Files.exists(dir.resolve("config.toml")));
        assertTrue(config.playerCommands());
    }

    @Test
    void playerCommandsCanBeTurnedOff() throws Exception {
        Files.writeString(dir.resolve("config.toml"), "lobby-servers = [\"lobby\"]\nplayer-commands = false\n");

        assertFalse(BridgeConfig.load(dir, NOPLogger.NOP_LOGGER).playerCommands());
    }
}
