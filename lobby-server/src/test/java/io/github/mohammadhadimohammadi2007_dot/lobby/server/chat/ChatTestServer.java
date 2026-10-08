package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.TestConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/** The real chat system on an in-memory server, with "Admin" as operator, for end-to-end tests. */
final class ChatTestServer {

    static final Pos SPAWN = new Pos(0.5, 41, 0.5);

    final Env env;
    final Instance instance;
    final ChatSystem chat;

    private ChatTestServer(Env env, Instance instance, ChatSystem chat) {
        this.env = env;
        this.instance = instance;
        this.chat = chat;
    }

    /** Starts chat with the bundled config files; {@code chatYml} may change chat.yml first. */
    static ChatTestServer start(Env env, Path dir, UnaryOperator<String> chatYml) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Path main = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(main, Files.readString(main).replace("operators: []", "operators: [\"Admin\"]"));
        Path chatFile = dir.resolve(ConfigManager.CHAT_FILE);
        Files.writeString(chatFile, chatYml.apply(Files.readString(chatFile)));
        config.load();

        Instance instance = env.createFlatInstance();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, info(instance)));
        LobbyText text = new LobbyText(config, placeholders);
        ChatSystem chat = ChatSystem.start(new ChatSystem.Dependencies(config, text, permissions, MuteService.NONE,
                bridge, null, null));
        EventNode<PlayerEvent> players = EventNode.type("chat-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(players);
        placeholders.register(players);
        chat.register(players, env.process().command());
        return new ChatTestServer(env, instance, chat);
    }

    ChatServices services() {
        return chat.service().services();
    }

    /** Joins a player who has already moved, so the new-player check lets them chat. */
    Joined join(String name) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        Player player = connection.connect(instance, SPAWN);
        services().players().moved(player.getUuid());
        return new Joined(player, connection.trackIncoming(SystemChatPacket.class));
    }

    /** Like {@link #join} but without recording received chat, which would dominate a load test. */
    Player joinQuietly(String name) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        Player player = connection.connect(instance, SPAWN);
        services().players().moved(player.getUuid());
        return player;
    }

    /** Sends {@code text} as {@code player}'s chat and waits until the whole pipeline ran. */
    void say(Joined player, String text) throws Exception {
        chat.service().submit(player.player(), text).get(5, TimeUnit.SECONDS);
    }

    /** A joined player and the chat lines they receive. */
    record Joined(Player player, Collector<SystemChatPacket> received) {

        List<Component> components() {
            return received.collect().stream().map(SystemChatPacket::message).toList();
        }

        List<String> lines() {
            return components().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
        }
    }

    private static ServerInfo info(Instance instance) {
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
                return List.of();
            }

            @Override
            public String bridgeStatus() {
                return "off";
            }
        };
    }
}
