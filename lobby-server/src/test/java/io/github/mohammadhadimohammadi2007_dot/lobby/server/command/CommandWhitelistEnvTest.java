package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.DeclareCommandsPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** commands.yml: what each group sees in the command tree and may type. */
@EnvTest
class CommandWhitelistEnvTest {

    @TempDir
    Path dir;

    /** Permissions by player name; "op" has everything. */
    private record FakePermissions(Map<String, Set<String>> byName, Map<String, Integer> weights)
            implements PermissionService {

        @Override
        public String name() {
            return "test";
        }

        @Override
        public boolean hasPermission(Player player, String permission) {
            Set<String> granted = byName.getOrDefault(player.getUsername(), Set.of());
            return granted.contains("*") || granted.contains(permission);
        }

        @Override
        public OptionalInt groupWeight(String group) {
            Integer weight = weights.get(group);
            return weight == null ? OptionalInt.empty() : OptionalInt.of(weight);
        }

        @Override
        public PlayerMeta meta(Player player) {
            return PlayerMeta.EMPTY;
        }
    }

    private final AtomicInteger runs = new AtomicInteger();

    private Command counting(String name, String... aliases) {
        Command command = new Command(name, aliases);
        command.setDefaultExecutor((sender, context) -> runs.incrementAndGet());
        return command;
    }

    private record Setup(Instance instance, List<Component> messages) {
    }

    private Setup start(Env env, String commandsYml, FakePermissions permissions) throws Exception {
        Files.writeString(dir.resolve(ConfigManager.COMMANDS_FILE), commandsYml);
        ConfigManager config = new ConfigManager(dir);
        config.load();
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        var commands = env.process().command();
        commands.register(counting("spawn"));
        commands.register(counting("hologram", "hd"));
        commands.register(counting("fly"));
        commands.register(counting("npc"));
        EventNode<PlayerEvent> node = EventNode.type("whitelist-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        new CommandWhitelist(() -> config.current().commands(), permissions, text, commands).register(node);
        return new Setup(env.createFlatInstance(), new ArrayList<>());
    }

    private static final String FILE = """
            enabled: true
            groups:
              default:
                commands: [spawn, fly]
              mod:
                weight: 10
                commands: [hd]
              jailed:
                weight: 90
                commands: ["-spawn"]
              admin:
                weight: 100
                commands: ["*", "-npc"]
            """;

    private Player join(Env env, Setup setup, String name) {
        return env.createConnection(new GameProfile(UUID.randomUUID(), name)).connect(setup.instance(), new Pos(0, 42, 0));
    }

    private static List<String> tree(Env env, Player player) {
        DeclareCommandsPacket packet = env.process().command().createDeclareCommandsPacket(player);
        return packet.nodes().stream().map(node -> node.name).filter(name -> !name.isEmpty()).sorted().toList();
    }

    private int typed(Env env, Player player, String command) {
        int before = runs.get();
        env.process().command().execute(player, command);
        return runs.get() - before;
    }

    @Test
    void playersOnlySeeAndRunTheCommandsOfTheirGroups(Env env) throws Exception {
        Setup setup = start(env, FILE, new FakePermissions(Map.of(
                "Mod", Set.of("lobby.commands.group.mod"),
                "Jailed", Set.of("group.jailed"),
                "Admin", Set.of("group.admin"),
                "Op", Set.of("*")), Map.of()));
        Player steve = join(env, setup, "Steve");
        Player mod = join(env, setup, "Mod");
        Player jailed = join(env, setup, "Jailed");
        Player admin = join(env, setup, "Admin");
        Player op = join(env, setup, "Op");

        assertEquals(List.of("fly", "spawn"), tree(env, steve));
        assertEquals(List.of("fly", "hd", "hologram", "spawn"), tree(env, mod), "an alias in the list allows the command");
        assertEquals(List.of("fly"), tree(env, jailed), "a heavier group hides what default allows");
        assertEquals(List.of("fly", "hd", "hologram", "spawn"), tree(env, admin), "* minus -npc");
        assertTrue(tree(env, op).contains("npc"), "lobby.commands.bypass (here: every permission) sees everything");

        assertEquals(1, typed(env, steve, "spawn"));
        assertEquals(0, typed(env, steve, "hologram"));
        assertEquals(0, typed(env, steve, "hd"));
        assertEquals(0, typed(env, jailed, "spawn"));
        assertEquals(1, typed(env, op, "npc"));
    }

    @Test
    void theLuckPermsWeightOutranksTheFile(Env env) throws Exception {
        Setup setup = start(env, """
                groups:
                  default:
                    commands: [spawn]
                  jailed:
                    weight: 90
                    commands: ["-fly"]
                  mod:
                    weight: 10
                    commands: [fly]
                """, new FakePermissions(Map.of("Both", Set.of("group.jailed", "group.mod")),
                Map.of("mod", 200)));
        Player both = join(env, setup, "Both");

        assertEquals(List.of("fly", "spawn"), tree(env, both), "mod (LuckPerms weight 200) beats jailed (90)");
    }

    @Test
    void aHiddenCommandAnswersLikeAnUnknownOneAndActionsMayStillRunIt(Env env) throws Exception {
        Setup setup = start(env, FILE, new FakePermissions(Map.of(), Map.of()));
        var connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(setup.instance(), new Pos(0, 42, 0));
        var chat = connection.trackIncoming(net.minestom.server.network.packet.server.play.SystemChatPacket.class);

        assertEquals(0, typed(env, steve, "npc"));
        env.process().command().execute(steve, "doesnotexist");
        List<String> answers = chat.collect().stream()
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message())).toList();
        assertEquals(List.of("Unknown or incomplete command.", "Unknown or incomplete command."), answers);

        // What PlayerCommandAction does for an NPC, menu or hotbar item.
        CommandWhitelist.runFromAction(() -> env.process().command().execute(steve, "npc"));
        assertEquals(1, runs.get(), "an action is not limited by commands.yml");
    }

    @Test
    void turnedOffEveryoneSeesEverything(Env env) throws Exception {
        Setup setup = start(env, "enabled: false\n", new FakePermissions(Map.of(), Map.of()));
        assertFalse(tree(env, join(env, setup, "Steve")).isEmpty());
        assertEquals(List.of("fly", "hd", "hologram", "npc", "spawn"), tree(env, join(env, setup, "Alex")));
    }
}
