package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuDefinition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItem;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.Material;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Several lobby instances of one map: where players land, what they see, and moving between them. */
@EnvTest
class LobbyInstancesEnvTest {

    private static final Pos SPAWN = new Pos(0.5, 41, 0.5);

    @TempDir
    Path dir;

    /** The config with the {@code lobbies:} section changed by {@code edits} (search and replace pairs). */
    private ConfigManager config(String... edits) throws Exception {
        ConfigManager manager = new ConfigManager(dir);
        manager.load();
        Path file = dir.resolve(ConfigManager.CONFIG_FILE);
        String text = Files.readString(file);
        for (int i = 0; i < edits.length; i += 2) {
            String search = edits[i];
            if (!text.contains(search)) {
                throw new IllegalStateException("config.yml no longer contains '" + search + "'");
            }
            text = text.replace(search, edits[i + 1]);
        }
        Files.writeString(file, text);
        manager.load();
        return manager;
    }

    private LobbyInstances lobbies(Env env, ConfigManager config) {
        PermissionService permissions = new OperatorPermissionService(config);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        return LobbyInstances.create((InstanceContainer) env.createFlatInstance(), config, text, permissions);
    }

    private Player join(Env env, Instance instance, String name) {
        TestConnection connection = env.createConnection(
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        return connection.connect(instance, SPAWN);
    }

    @Test
    void oneInstanceByDefaultIsTheMapItself(Env env) throws Exception {
        ConfigManager config = config();
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        LobbyInstances lobbies = LobbyInstances.create(map, config,
                new LobbyText(config, new PlaceholderService(new PlaceholderRegistry())),
                new OperatorPermissionService(config));

        assertEquals(1, lobbies.count());
        assertSame(map, lobbies.forJoin(), "with one lobby no shared instance is needed");
        assertEquals(1, lobbies.numberOf(join(env, map, "Steve")));
    }

    @Test
    void joiningPlayersSpreadOverTheInstances(Env env) throws Exception {
        LobbyInstances lobbies = lobbies(env, config("instances: 1", "instances: 3"));
        assertEquals(3, lobbies.count());

        for (int number = 1; number <= 6; number++) {
            join(env, lobbies.forJoin(), "Player" + number);
        }

        assertEquals(List.of(2, 2, 2), List.of(lobbies.onlineIn(1), lobbies.onlineIn(2), lobbies.onlineIn(3)));
    }

    @Test
    void fillFirstUsesTheNextInstanceOnlyWhenOneIsFull(Env env) throws Exception {
        LobbyInstances lobbies = lobbies(env, config("instances: 1", "instances: 3",
                "players-per-instance: 50", "players-per-instance: 2",
                "join: least-players", "join: fill-first"));

        for (int number = 1; number <= 3; number++) {
            join(env, lobbies.forJoin(), "Player" + number);
        }

        assertEquals(List.of(2, 1, 0), List.of(lobbies.onlineIn(1), lobbies.onlineIn(2), lobbies.onlineIn(3)));
    }

    @Test
    void playersInDifferentInstancesDoNotSeeEachOther(Env env) throws Exception {
        LobbyInstances lobbies = lobbies(env, config("instances: 1", "instances: 2"));
        Player first = join(env, lobbies.byNumber(1), "First");
        Player second = join(env, lobbies.byNumber(2), "Second");
        Player neighbour = join(env, lobbies.byNumber(1), "Neighbour");
        env.tick();

        assertFalse(first.getViewers().contains(second), "another instance is another world");
        assertFalse(second.getViewers().contains(first));
        assertTrue(first.getViewers().contains(neighbour), "but the same instance is still shared");
        assertEquals(1, lobbies.numberOf(first));
        assertEquals(2, lobbies.numberOf(second));
    }

    @Test
    void switchingTellsThePlayerWhatHappened(Env env) throws Exception {
        LobbyInstances lobbies = lobbies(env, config("instances: 1", "instances: 2"));
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(lobbies.byNumber(1), SPAWN);
        Collector<SystemChatPacket> messages = connection.trackIncoming(SystemChatPacket.class);

        lobbies.switchTo(steve, 2);
        env.tick();
        assertSame(lobbies.byNumber(2), steve.getInstance());
        assertEquals(List.of("[Lobby] You are now in lobby 2."), lines(messages));

        // A lobby that does not exist, and the one the player is already in, only answer.
        messages = connection.trackIncoming(SystemChatPacket.class);
        lobbies.switchTo(steve, 7);
        lobbies.switchTo(steve, 2);
        env.tick();
        assertEquals(List.of("[Lobby] There is no lobby 7. This server has 2.",
                        "[Lobby] You are already in lobby 2."),
                lines(messages));
        assertSame(lobbies.byNumber(2), steve.getInstance());
    }

    @Test
    void aFullInstanceOnlyLetsStaffIn(Env env) throws Exception {
        ConfigManager config = config("instances: 1", "instances: 2",
                "players-per-instance: 50", "players-per-instance: 1",
                "operators: []", "operators: [\"Admin\"]");
        LobbyInstances lobbies = lobbies(env, config);
        join(env, lobbies.byNumber(2), "Resident");

        TestConnection player = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = player.connect(lobbies.byNumber(1), SPAWN);
        Collector<SystemChatPacket> refused = player.trackIncoming(SystemChatPacket.class);
        lobbies.switchTo(steve, 2);
        env.tick();
        assertEquals(List.of("[Lobby] Lobby 2 is full."), lines(refused));
        assertSame(lobbies.byNumber(1), steve.getInstance());

        // Operators have every permission, so lobby.lobbies.join-full as well.
        TestConnection staff = env.createConnection(new GameProfile(UUID.randomUUID(), "Admin"));
        Player admin = staff.connect(lobbies.byNumber(1), SPAWN);
        lobbies.switchTo(admin, 2);
        env.tick();
        assertSame(lobbies.byNumber(2), admin.getInstance());
    }

    @Test
    void theSelectorHasOneItemPerLobby(Env env) throws Exception {
        ConfigManager config = config("instances: 1", "instances: 3");
        LobbyInstances lobbies = lobbies(env, config);
        Player steve = join(env, lobbies.byNumber(2), "Steve");

        MenuDefinition menu = new LobbySelectorMenu(config, lobbies, new NetworkState()).build(steve);

        assertEquals(LobbySelectorMenu.NAME, menu.name());
        assertEquals(3, menu.items().size());
        assertEquals(List.of(0), menu.items().getFirst().slots());
        // The player's own lobby glows and has nothing to click.
        assertTrue(menu.items().get(1).glow());
        assertTrue(menu.items().get(1).actions().isEmpty());
        assertFalse(menu.items().getFirst().glow());
        assertFalse(menu.items().getFirst().actions().isEmpty());
        // The counts are placeholders, so they stay live while the menu is open.
        assertTrue(menu.items().get(2).lore().getFirst().contains("%lobby_online_3%"),
                menu.items().get(2).lore().toString());
    }

    @Test
    void theSelectorAlsoListsTheNetworksOtherLobbyServers(Env env) throws Exception {
        ConfigManager config = config("instances: 1", "instances: 2");
        LobbyInstances lobbies = lobbies(env, config);
        Player steve = join(env, lobbies.byNumber(1), "Steve");
        String self = config.current().config().server().name();
        NetworkState network = new NetworkState();
        network.update(new BridgeMessage.NetworkSnapshot(10, Map.of("lobby-2", 4), Map.of(
                LobbySelectorMenu.LOBBIES_GROUP, List.of(self, "lobby-2", "lobby-3"))));
        network.update(new BridgeMessage.ServerStatus(Map.of(
                "lobby-2", new BridgeMessage.ServerStatus.Status(true, 100),
                "lobby-3", new BridgeMessage.ServerStatus.Status(false, 0))));

        MenuDefinition menu = new LobbySelectorMenu(config, lobbies, network).build(steve);

        // Two instances here, then the other servers from the next row on; this server is not listed twice.
        assertEquals(4, menu.items().size());
        MenuItem online = menu.items().get(2);
        assertEquals(List.of(9), online.slots());
        assertEquals(Material.BOOK, online.material());
        assertTrue(online.lore().getFirst().contains("%bungee_lobby-2%"), online.lore().toString());
        assertFalse(online.actions().isEmpty(), "a click connects to it");
        MenuItem offline = menu.items().get(3);
        assertEquals(Material.BARRIER, offline.material());
        assertTrue(offline.actions().isEmpty(), "an offline server cannot be joined");
    }

    private static List<String> lines(Collector<SystemChatPacket> messages) {
        return messages.collect().stream()
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message()))
                .toList();
    }
}
