package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.LobbyCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.SpawnCommand;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.protection.ProtectionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Joins fake players to an in-memory server and checks spawn, commands, permissions and protections. */
@EnvTest
class LobbyBasicsEnvTest {

    private static final Pos SPAWN = new Pos(0.5, 65, 0.5);

    @TempDir
    Path dir;

    /** Loads the default config with "Admin" as operator. */
    private ConfigManager config() throws Exception {
        ConfigManager manager = new ConfigManager(dir);
        manager.load();
        Path file = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(file, Files.readString(file).replace("operators: []", "operators: [\"Admin\"]"));
        manager.load();
        return manager;
    }

    private static PermissionService register(Env env, ConfigManager config, Instance instance) {
        PermissionService permissions = new OperatorPermissionService(config);
        env.process().command().register(new SpawnCommand(config, permissions));
        env.process().command().register(new LobbyCommand(config, permissions, fakeInfo(instance)));
        new ProtectionListener(config, permissions).register(env.process().eventHandler());
        EventNode<PlayerEvent> players = EventNode.type("test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(players);
        new SpawnListener(config, instance).register(players);
        return permissions;
    }

    @Test
    void spawnCommandTeleportsAndReplies(Env env) throws Exception {
        ConfigManager config = config();
        Instance instance = env.createFlatInstance();
        register(env, config, instance);
        TestConnection connection = env.createConnection();
        Player player = connection.connect(instance, new Pos(20, 41, 20));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().command().execute(player, "spawn");
        env.tick();

        assertEquals(SPAWN, player.getPosition());
        assertEquals(List.of("[Lobby] Teleported to spawn."), texts(chat));
    }

    @Test
    void normalPlayerCannotUseLobbyCommands(Env env) throws Exception {
        ConfigManager config = config();
        Instance instance = env.createFlatInstance();
        register(env, config, instance);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player player = connection.connect(instance, SPAWN);
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().command().execute(player, "lobby info");

        assertEquals(List.of("[Lobby] You don't have permission to do that."), texts(chat));
    }

    @Test
    void operatorCanSeeLobbyInfo(Env env) throws Exception {
        ConfigManager config = config();
        Instance instance = env.createFlatInstance();
        register(env, config, instance);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "admin"));
        Player player = connection.connect(instance, SPAWN);
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);

        env.process().command().execute(player, "lobby info");

        List<String> lines = texts(chat);
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().startsWith("Lobby info"), lines.getFirst());
        assertTrue(lines.getFirst().contains("World: test-map (polar, 1 chunks)"), lines.getFirst());
    }

    @Test
    void protectionsBlockNormalPlayersButNotBypass(Env env) throws Exception {
        ConfigManager config = config();
        Instance instance = env.createFlatInstance();
        register(env, config, instance);
        Player steve = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"))
                .connect(instance, SPAWN);
        Player admin = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin"))
                .connect(instance, SPAWN);

        assertTrue(breakBlock(steve, instance).isCancelled(), "normal player must not break blocks");
        assertFalse(breakBlock(admin, instance).isCancelled(), "operator has lobby.bypass.protection");

        ItemDropEvent drop = new ItemDropEvent(steve, ItemStack.of(Material.STONE));
        EventDispatcher.call(drop);
        assertTrue(drop.isCancelled(), "normal player must not drop items");
    }

    @Test
    void fallingIntoTheVoidReturnsToSpawn(Env env) throws Exception {
        ConfigManager config = config();
        Instance instance = env.createFlatInstance();
        register(env, config, instance);
        Player player = env.createPlayer(instance, new Pos(5, 41, 5));

        player.addPacketToQueue(new ClientPlayerPositionPacket(new Pos(5, -10, 5), (byte) 0));
        player.interpretPacketQueue();
        env.tick();

        assertEquals(SPAWN, player.getPosition());
    }

    private static PlayerBlockBreakEvent breakBlock(Player player, Instance instance) {
        PlayerBlockBreakEvent event = new PlayerBlockBreakEvent(player, instance, Block.STONE, Block.AIR,
                new BlockVec(0, 39, 0), BlockFace.TOP);
        EventDispatcher.call(event);
        return event;
    }

    private static List<String> texts(Collector<SystemChatPacket> chat) {
        return chat.collect().stream()
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message()))
                .toList();
    }

    private static ServerInfo fakeInfo(Instance instance) {
        LobbyWorld world = new LobbyWorld((InstanceContainer) instance, "test-map", WorldFormat.POLAR, 1, 0, 0, false);
        return new ServerInfo() {
            @Override
            public String modeDescription() {
                return "standalone (offline mode)";
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
                return List.of(IntegrationStatus.disabled("LiteBans"));
            }

            @Override
            public String bridgeStatus() {
                return "disabled";
            }
        };
    }
}
