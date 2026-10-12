package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.ChatChangingAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.DependentAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.FailingListenerAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.RecordingAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.testaddons.ThrowingAddon;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real addon jars in the addons folder of a whole lobby: loading, isolation and cleanup. */
@EnvTest
class AddonEnvTest {

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
                // The test failed while building: at least close the addons' jars, so the folder can be deleted.
                lobby.addons().disableAll();
            }
        }
        LobbyEvents.deactivate();
        System.getProperties().keySet().removeIf(key -> key.toString().startsWith("recording.")
                || key.toString().equals("order"));
    }

    private Path addons() {
        return dir.resolve("addons");
    }

    /** Starts the lobby as LobbyMain does: addons first, then the files, then everything else. */
    private LobbyServer start() throws Exception {
        config = new ConfigManager(dir);
        LobbyServer lobby = new LobbyServer(config);
        started.add(lobby);
        lobby.loadAddons();
        config.load();
        LobbyServerTestAccess.build(lobby);
        return lobby;
    }

    private Player join(Env env, LobbyServer lobby, String name) {
        return env.createConnection(new GameProfile(UUID.randomUUID(), name))
                .connect(lobby.world().instance(), SpawnListener.spawnPosition(config.current().config()));
    }

    private static AddonState state(LobbyServer lobby, String id) {
        return lobby.addons().infos().stream().filter(addon -> addon.id().equals(id)).findFirst().orElseThrow().state();
    }

    private static String detail(LobbyServer lobby, String id) {
        return lobby.addons().infos().stream().filter(addon -> addon.id().equals(id)).findFirst().orElseThrow().detail();
    }

    private static List<String> addonNodes() {
        return MinecraftServer.getGlobalEventHandler().getChildren().stream()
                .map(EventNode::getName).filter(name -> name.startsWith("addon-")).toList();
    }

    @Test
    void anEmptyAddonsFolderStartsNothing(Env env) throws Exception {
        LobbyServer lobby = start();
        join(env, lobby, "Steve");

        assertTrue(Files.isDirectory(addons()), "the folder is made so admins see where addons go");
        assertFalse(lobby.addons().any());
        assertFalse(LobbyEvents.active(), "no addon listens, so no lobby event is even created");
        assertEquals(List.of(), addonNodes());
        assertFalse(Files.exists(dir.resolve("data").resolve("addon-settings")));
    }

    @Test
    void everythingAnAddonRegistersWorksAndIsGoneAfterItStops(Env env) throws Exception {
        AddonJars.addon(addons(), "recording", RecordingAddon.class, "", Map.of("config.yml", "greeting: hello\n"));
        LobbyServer lobby = start();
        Player steve = join(env, lobby, "Steve");

        assertEquals(AddonState.ENABLED, state(lobby, "recording"));
        assertEquals("hidden", System.getProperty("recording.internal"), "addons cannot load the lobby's classes");
        assertEquals("hello", System.getProperty("recording.config"), "the default config is copied from the jar");
        assertTrue(Files.isRegularFile(addons().resolve("recording").resolve("config.yml")));
        var text = lobby.text().placeholders();
        assertEquals("fixed", text.plainText("%recording_constant%", null));
        assertEquals("hi Steve", text.plainText("%recording_name%", steve));
        ActionParser.parse("recording_mark: for %player_name%").run(new ActionContext(steve, lobby.actions(), "test"));
        assertEquals("for Steve", System.getProperty("recording.mark"));
        assertTrue(MinecraftServer.getCommandManager().commandExists("recording"));
        assertTrue(lobby.menus().exists("recording-menu"));
        assertEquals(List.of("addon-recording"), addonNodes());
        assertTrue(LobbyEvents.active());

        LobbyServerTestAccess.stop(lobby);
        started.remove(lobby);

        assertEquals("true", System.getProperty("recording.disabled"));
        assertEquals(AddonState.DISABLED, state(lobby, "recording"));
        assertEquals("%recording_constant%", text.plainText("%recording_constant%", null));
        assertFalse(ActionParser.typeNames().contains("recording_mark"));
        assertFalse(MinecraftServer.getCommandManager().commandExists("recording"));
        assertFalse(lobby.menus().exists("recording-menu"));
        assertEquals(List.of(), addonNodes());
    }

    @Test
    void dependenciesDecideTheOrderAndBrokenAddonsAreReported(Env env) throws Exception {
        // "a-..." would come first by name; its dependency must still be enabled before it.
        AddonJars.addon(addons(), "a-dependent", DependentAddon.class, "\"depends\": [\"b-base\"]", Map.of());
        AddonJars.addon(addons(), "b-base", DependentAddon.class, "", Map.of());
        AddonJars.addon(addons(), "lonely", DependentAddon.class, "\"depends\": [\"absent\"]", Map.of());
        AddonJars.addon(addons(), "circle-a", DependentAddon.class, "\"depends\": [\"circle-b\"]", Map.of());
        AddonJars.addon(addons(), "circle-b", DependentAddon.class, "\"soft-depends\": [\"circle-a\"]", Map.of());
        AddonJars.addon(addons(), "plain", DependentAddon.class, "", Map.of());
        Files.writeString(addons().resolve("notes.txt"), "not a jar, ignored");
        AddonJars.raw(addons(), "not-an-addon.jar", Map.of("readme.txt", "hello"));
        String json = "{\"id\": \"future\", \"version\": \"1\", \"main\": \"x.Y\", \"api-version\": \"9.0\"}";
        AddonJars.raw(addons(), "future.jar", Map.of("addon.json", json));

        LobbyServer lobby = start();

        assertEquals("b-base;a-dependent;plain;", System.getProperty("order"));
        assertEquals(AddonState.MISSING_DEPENDENCY, state(lobby, "lonely"));
        assertTrue(detail(lobby, "lonely").contains("absent"), detail(lobby, "lonely"));
        assertEquals(AddonState.DEPENDENCY_CYCLE, state(lobby, "circle-a"));
        assertEquals(AddonState.DEPENDENCY_CYCLE, state(lobby, "circle-b"));
        assertEquals(AddonState.INCOMPATIBLE, state(lobby, "future"));
        assertEquals(AddonState.INVALID, state(lobby, "not-an-addon.jar"));
        assertEquals(5, AddonChecks.doctor(lobby.addons().infos()).size(), "lonely, two circles, future, not-an-addon");
    }

    @Test
    void anAddonThatFailsToStartDoesNotStopTheOthers(Env env) throws Exception {
        AddonJars.addon(addons(), "recording", RecordingAddon.class, "", Map.of());
        AddonJars.addon(addons(), "throwing", ThrowingAddon.class, "", Map.of());
        AddonJars.addon(addons(), "needs-throwing", DependentAddon.class, "\"depends\": [\"throwing\"]", Map.of());

        LobbyServer lobby = start();

        assertEquals(AddonState.FAILED, state(lobby, "throwing"));
        assertTrue(detail(lobby, "throwing").contains("broken on purpose"), detail(lobby, "throwing"));
        assertEquals(AddonState.MISSING_DEPENDENCY, state(lobby, "needs-throwing"));
        assertEquals(AddonState.ENABLED, state(lobby, "recording"));
        assertEquals("%throwing_x%", lobby.text().placeholders().plainText("%throwing_x%", null),
                "what it registered in onLoad is gone");
    }

    @Test
    void aListenerThatThrowsDisablesOnlyItsAddon(Env env) throws Exception {
        AddonJars.addon(addons(), "failing", FailingListenerAddon.class, "", Map.of());
        AddonJars.addon(addons(), "chat-changer", ChatChangingAddon.class, "", Map.of());
        LobbyServer lobby = start();
        Player steve = join(env, lobby, "Steve");
        var chat = lobby.chat().service();
        chat.services().players().moved(steve.getUuid());

        chat.submit(steve, "my secret").get(5, TimeUnit.SECONDS);
        // The failed addon stops running code at once and is cleaned up in the background.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!addonNodes().equals(List.of("addon-chat-changer")) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        chat.submit(steve, "this is forbidden").get(5, TimeUnit.SECONDS);
        chat.submit(steve, "another secret").get(5, TimeUnit.SECONDS);

        assertEquals(AddonState.FAILED, state(lobby, "failing"));
        assertTrue(detail(lobby, "failing").contains("listener broken on purpose"), detail(lobby, "failing"));
        assertEquals(AddonState.ENABLED, state(lobby, "chat-changer"));
        assertEquals(List.of("addon-chat-changer"), addonNodes());
        List<String> sent = new ArrayList<>();
        chat.onChatThread(() -> chat.services().history().messages().stream().map(ChatMessage::text).forEach(sent::add))
                .get(5, TimeUnit.SECONDS);
        assertEquals(List.of("my *****", "another *****"), sent);
    }

    @Test
    void anAddonOthersUseIsNotReloaded(Env env) throws Exception {
        AddonJars.addon(addons(), "recording", RecordingAddon.class, "", Map.of());
        AddonJars.addon(addons(), "user", DependentAddon.class, "\"depends\": [\"recording\"]", Map.of());
        LobbyServer lobby = start();

        AddonManager.ReloadResult refused = lobby.addons().reload("recording");
        assertFalse(refused.ok());
        assertTrue(refused.message().contains("user"), refused.message());
        assertEquals(AddonState.ENABLED, state(lobby, "recording"), "a refused reload changes nothing");

        AddonManager.ReloadResult user = lobby.addons().reload("user");
        assertTrue(user.ok(), user.message());
        assertEquals(AddonState.ENABLED, state(lobby, "user"));
        assertFalse(lobby.addons().reload("nobody").ok());
    }

    @Test
    void reloadingAnAddonStartsItAgainWithItsRegistrations(Env env) throws Exception {
        AddonJars.addon(addons(), "recording", RecordingAddon.class, "", Map.of());
        LobbyServer lobby = start();
        System.clearProperty("recording.enabled");

        AddonManager.ReloadResult reloaded = lobby.addons().reload("recording");
        assertTrue(reloaded.ok(), reloaded.message());
        assertEquals("true", System.getProperty("recording.disabled"));
        assertEquals("true", System.getProperty("recording.enabled"));
        assertEquals("fixed", lobby.text().placeholders().plainText("%recording_constant%", null));
        assertTrue(MinecraftServer.getCommandManager().commandExists("recording"));
        assertEquals(List.of("addon-recording"), addonNodes());
    }
}
