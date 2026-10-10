package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The whole lobby, wired as {@link LobbyServer} does it, with the bundled configuration. */
@EnvTest
class LobbyServerEnvTest {

    @TempDir
    Path dir;

    @Test
    void theInfoCommandShowsTheClientVersionTheProxyReported(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = new LobbyServer(config);
        lobby.build();
        try {
            UUID old = UUID.randomUUID();
            BridgeTestAccess.reportVersion(lobby.bridge(), old, ProtocolVersions.V1_8);
            Player legacy = env.createConnection(new GameProfile(old, "Old")).connect(lobby.world().instance(),
                    SpawnListener.spawnPosition(config.current().config()));
            Player modern = env.createConnection(new GameProfile(UUID.randomUUID(), "New"))
                    .connect(lobby.world().instance(), SpawnListener.spawnPosition(config.current().config()));

            assertEquals("1.8.x (protocol 47), legacy, reported by the proxy", lobby.clientOf(legacy));
            assertTrue(lobby.clientOf(modern).endsWith("modern, not reported by a proxy (this server's own version)"),
                    lobby.clientOf(modern));
        } finally {
            lobby.stopFeatures();
        }
    }
}
