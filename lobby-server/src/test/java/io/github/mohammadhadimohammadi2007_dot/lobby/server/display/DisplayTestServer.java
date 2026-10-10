package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.TestConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A display service on a test server. A player's client version and rank come from their name, so tests
 * can mix clients: a name containing {@code Old} is 1.8, {@code Mid} is 1.20.1, anything else the newest
 * version; {@code Admin} has weight 100, {@code Vip} weight 10, everyone else none.
 */
final class DisplayTestServer {

    static final Pos ORIGIN = new Pos(0.5, 41, 0.5);

    final ConfigManager config;
    final LobbyText text;
    final PlaceholderService placeholders;
    final PermissionService permissions;
    final ClientObjectRenderer renderer;
    final TeamManager teams;
    final DisplayService displays;

    /** @param displayYml the display.yml to use, or {@code null} for the bundled one */
    DisplayTestServer(Env env, Path dir, InstanceContainer map, String displayYml) throws Exception {
        if (displayYml != null) {
            Files.writeString(dir.resolve(ConfigManager.DISPLAY_FILE), displayYml);
        }
        config = new ConfigManager(dir);
        config.load();
        permissions = new NamedRanks();
        BridgeService bridge = new BridgeService(false);
        placeholders = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, info(map), LobbyInstanceInfo.SINGLE));
        text = new LobbyText(config, placeholders);
        renderer = new ClientObjectRenderer();
        teams = new TeamManager(DisplayTestServer::protocolOf, false);
        displays = new DisplayService(config, text, permissions, teams, DisplayTestServer::protocolOf);
        EventNode<PlayerEvent> node = EventNode.type("display-test-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        teams.register(node);
    }

    void shutdown() {
        displays.shutdown();
        renderer.shutdown();
    }

    static int protocolOf(Player player) {
        String name = player.getUsername();
        if (name.contains("Old")) {
            return ProtocolVersions.V1_8;
        }
        if (name.contains("Mid")) {
            return 763;
        }
        return MinecraftServer.PROTOCOL_VERSION;
    }

    static TestConnection join(Env env, Instance instance, String name) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), name));
        connection.connect(instance, ORIGIN);
        return connection;
    }

    /** Everyone has every permission; ranks come from the name. */
    private static final class NamedRanks implements PermissionService {

        @Override
        public String name() {
            return "test";
        }

        @Override
        public boolean hasPermission(Player player, String permission) {
            return !player.getUsername().contains("Guest");
        }

        @Override
        public PlayerMeta meta(Player player) {
            String name = player.getUsername();
            if (name.contains("Admin")) {
                return new PlayerMeta("<red>[Admin] ", "", "admin", Map.of(), 100);
            }
            if (name.contains("Vip")) {
                return new PlayerMeta("<gold>[VIP] ", "", "vip", Map.of(), 10);
            }
            return new PlayerMeta("<gray>", "", "default", Map.of(), 0);
        }
    }

    private static ServerInfo info(InstanceContainer map) {
        LobbyWorld world = new LobbyWorld(map, "test-map", WorldFormat.POLAR, 1, 0, 0, false);
        return new ServerInfo() {
            @Override
            public String modeDescription() {
                return "standalone";
            }

            @Override
            public double tps() {
                return 20;
            }

            @Override
            public double mspt() {
                return 1;
            }

            @Override
            public LobbyWorld world() {
                return world;
            }

            @Override
            public List<IntegrationStatus> integrations() {
                return List.of();
            }

            @Override
            public String bridgeStatus() {
                return "off";
            }
        };
    }
}
