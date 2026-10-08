package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The built-in namespaces with real (fake) players. */
@EnvTest
class BuiltinPlaceholdersEnvTest {

    @TempDir
    Path dir;

    /** A permission service whose rank can be changed by the test, like LuckPerms. */
    private static final class FakeRanks implements PermissionService {
        volatile PlayerMeta meta = new PlayerMeta("&7", "", "default", Map.of("color", "&a"));
        final AtomicInteger metaCalls = new AtomicInteger();
        Consumer<UUID> listener = id -> { };

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean hasPermission(Player player, String permission) {
            return permission.equals("lobby.vip");
        }

        @Override
        public PlayerMeta meta(Player player) {
            metaCalls.incrementAndGet();
            return meta;
        }

        @Override
        public void onMetaChange(Consumer<UUID> listener) {
            this.listener = listener;
        }

        void changeRank(UUID player, PlayerMeta newMeta) {
            meta = newMeta;
            listener.accept(player);
        }
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void builtinNamespacesWork(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        FakeRanks ranks = new FakeRanks();
        BridgeService bridge = new BridgeService(false);
        PlaceholderService service = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(service.registry(),
                new BuiltinPlaceholders.Sources(config, ranks, MuteService.NONE, bridge, info(),
                        LobbyInstanceInfo.SINGLE));
        ranks.onMetaChange(service::invalidate);

        Instance instance = env.createFlatInstance();
        Player steve = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve")).connect(instance, new Pos(0, 42, 0));

        assertEquals("Steve default yes no", plain(service.render(
                "%player_name% %luckperms_primary_group_name% %luckperms_has_permission_lobby.vip% %luckperms_has_permission_x%", steve)));
        assertEquals(MinecraftServer.VERSION_NAME, plain(service.render("%player_client_version%", steve)));
        // %lobby_id% is which lobby instance the player is in, %lobby_name% this server's name.
        assertEquals("lobby 1 1 lobby 200", plain(service.render(
                "%server_name% %server_online% %lobby_id% %lobby_name% %server_max_players%", steve)));
        assertEquals("0 0 0", plain(service.render("%bungee_total% %bungee_bw-1% %group_online_bedwars%", steve)));
        assertEquals("no", plain(service.render("%litebans_muted%", steve)));

        // Prefix is cached: rendering again does not ask the permission service.
        service.render("%luckperms_prefix%", steve);
        int calls = ranks.metaCalls.get();
        service.render("%luckperms_prefix% %luckperms_prefix%", steve);
        assertEquals(calls, ranks.metaCalls.get(), "cached until the rank changes");

        // A rank change (LuckPerms recalculation) drops the cached values.
        ranks.changeRank(steve.getUuid(), new PlayerMeta("&6[VIP] ", "", "vip", Map.of()));
        assertEquals("[VIP] Steve", plain(service.render("%luckperms_prefix%%player_name%", steve)));
        assertEquals("vip", plain(service.render("%luckperms_primary_group_name%", steve)));
    }

    @Test
    void chatStyleRenderingUsesTargetAndViewer(Env env) throws Exception {
        PlaceholderService service = new PlaceholderService(new PlaceholderRegistry());
        service.registry().register("rel", params -> new Placeholder.Relational(
                (viewer, target) -> viewer.getUsername() + " sees " + target.getUsername(), false));
        service.registry().register("player", params -> Placeholder.player(Player::getUsername));

        Instance instance = env.createFlatInstance();
        Player sender = env.createConnection(new GameProfile(UUID.randomUUID(), "Sender")).connect(instance, new Pos(0, 42, 0));
        Player reader = env.createConnection(new GameProfile(UUID.randomUUID(), "Reader")).connect(instance, new Pos(0, 42, 0));

        assertEquals("Sender: Reader sees Sender", plain(service.render("%player_name%: %rel_x%", sender, reader)));
    }

    @Test
    void bridgeCountsAppearInPlaceholders(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        BridgeService bridge = new BridgeService(true);
        bridge.networkState().update(new BridgeMessage.NetworkSnapshot(30,
                Map.of("bw-1", 12, "bw-2", 8, "lobby", 10),
                Map.of("bedwars", List.of("bw-1", "bw-2"), "lobbies", List.of("lobby", "lobby-2"))));
        PlaceholderService service = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(service.registry(),
                new BuiltinPlaceholders.Sources(config, new FakeRanks(), MuteService.NONE, bridge, info(),
                        LobbyInstanceInfo.SINGLE));

        assertEquals("30 12 20 2", plain(service.render(
                "%bungee_total% %bungee_bw-1% %group_online_bedwars% %lobby_servers%", null)));
    }

    private static ServerInfo info() {
        return new ServerInfo() {
            @Override
            public String modeDescription() {
                return "test";
            }

            @Override
            public double tps() {
                return 20;
            }

            @Override
            public double mspt() {
                return 1.5;
            }

            @Override
            public LobbyWorld world() {
                return null;
            }

            @Override
            public List<IntegrationStatus> integrations() {
                return List.of();
            }

            @Override
            public String bridgeStatus() {
                return "disabled";
            }
        };
    }
}
