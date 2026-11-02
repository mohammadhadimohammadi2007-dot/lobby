package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.ClickType;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.NpcClickEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.ExampleUserAddon;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The example addon (examples/addon-example) in a real lobby, with a second addon that uses its service and its
 * event: what the developer guide promises, working end to end.
 */
@EnvTest
class ExampleAddonTest {

    @TempDir
    Path dir;

    private final List<LobbyServer> started = new ArrayList<>();
    private ConfigManager config;

    @AfterEach
    void stop() {
        for (LobbyServer lobby : started) {
            try {
                LobbyServerTestAccess.stop(lobby);
            } catch (RuntimeException notBuilt) {
                lobby.addons().disableAll();
            }
        }
        LobbyEvents.deactivate();
        System.getProperties().keySet().removeIf(key -> key.toString().startsWith("user."));
    }

    private LobbyServer start() throws Exception {
        config = new ConfigManager(dir);
        LobbyServer lobby = new LobbyServer(config);
        started.add(lobby);
        lobby.loadAddons();
        config.load();
        LobbyServerTestAccess.build(lobby);
        return lobby;
    }

    private static AddonState state(LobbyServer lobby, String id) {
        return lobby.addons().infos().stream().filter(addon -> addon.id().equals(id)).findFirst().orElseThrow().state();
    }

    private static boolean exampleZoneAt(LobbyServer lobby, Pos position) {
        return lobby.regions().regionsAt(position).stream().map(Region::owner).anyMatch("addon:example"::equals);
    }

    @Test
    void theExampleAddonRunsAndAnotherAddonUsesItsServiceAndEvent(Env env) throws Exception {
        Path jar = Path.of(System.getProperty("lobby.example.addon"));
        assertTrue(Files.isRegularFile(jar), "built by :example-addon:jar before the tests");
        Path addons = dir.resolve("addons");
        Files.createDirectories(addons);
        Files.copy(jar, addons.resolve("example-addon.jar"));
        AddonJars.addon(addons, "example-user", ExampleUserAddon.class, "\"depends\": [\"example\"]", Map.of());

        LobbyServer lobby = start();
        assertEquals(AddonState.ENABLED, state(lobby, "example"));
        assertEquals(AddonState.ENABLED, state(lobby, "example-user"));
        assertTrue(Files.isRegularFile(addons.resolve("example").resolve("config.yml")), "its defaults are copied");
        assertEquals("example", System.getProperty("user.provider"), "the service is there from onLoad on");
        assertEquals("refused", System.getProperty("user.steal"), "only one addon provides a service");
        assertEquals("[]", System.getProperty("user.own"), "each addon has its own regions");
        assertTrue(MinecraftServer.getCommandManager().commandExists("clicks"));
        assertTrue(lobby.menus().exists("example-clicks"));

        Pos spawn = SpawnListener.spawnPosition(config.current().config());
        assertTrue(exampleZoneAt(lobby, spawn), "the zone of config.yml is around the spawn");
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Collector<SystemChatPacket> chat = connection.trackIncoming(SystemChatPacket.class);
        Player steve = connection.connect(lobby.world().instance(), spawn);
        env.tickWhile(() -> !"0".equals(lobby.text().placeholders().plainText("%example_clicks%", steve)),
                java.time.Duration.ofSeconds(5));
        assertEquals("0", lobby.text().placeholders().plainText("%example_clicks%", steve));

        for (int i = 0; i < 10; i++) {
            EventDispatcher.call(new NpcClickEvent(steve, "guide", ClickType.RIGHT));
        }
        assertEquals("early,normal,late", System.getProperty("user.order"), "priorities, not registration order");
        assertEquals("10", System.getProperty("user.counted"), "the other addon reads the count through the service");
        assertTrue(System.getProperty("user.regions", "").contains("addon:example/zone"), System.getProperty("user.regions"));
        List<String> messages = chat.collect().stream()
                .map(packet -> PlainTextComponentSerializer.plainText().serialize(packet.message())).toList();
        assertTrue(messages.stream().anyMatch(text -> text.contains("10th click")), "the milestone message: " + messages);
        assertTrue(messages.stream().anyMatch(text -> text.contains("example zone")), "greeted in the zone: " + messages);

        ActionParser.parse("example_reset").run(new ActionContext(steve, lobby.actions(), "test"));
        Thread.sleep(1100);
        assertEquals("0", lobby.text().placeholders().plainText("%example_clicks%", steve), "its action reset it");

        LobbyServerTestAccess.stop(lobby);
        started.remove(lobby);
        assertFalse(exampleZoneAt(lobby, spawn), "its region is gone with it");
        assertFalse(MinecraftServer.getCommandManager().commandExists("clicks"));
    }
}
