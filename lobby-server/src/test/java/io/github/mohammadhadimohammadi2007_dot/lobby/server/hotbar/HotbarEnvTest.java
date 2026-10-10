package io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hotbar items and player visibility on a running server, with the bundled hotbar.yml. */
@EnvTest
class HotbarEnvTest {

    private static final Pos SPAWN = new Pos(0, 41, 0);

    @TempDir
    Path dir;

    private final List<ChatSettingsService> settingsServices = new ArrayList<>();

    @AfterEach
    void stop() {
        settingsServices.forEach(ChatSettingsService::flush);
    }

    private record Setup(HotbarService hotbar, VisibilityService visibility, MenuService menus,
                         ChatSettingsService settings, ConfigManager config) {
    }

    private Setup start(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        // "Staff" is an operator, so they have every permission, the staff one included.
        Path main = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(main, Files.readString(main).replace("operators: []", "operators: [\"Staff\"]"));
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        ActionServices actions = new ActionServices(config, text, permissions, bridge);
        MenuService menus = new MenuService(() -> config.current().menus(), text, actions, bridge);
        actions.menus(menus);
        ChatSettingsService settings = new ChatSettingsService(new FileSettingsStore(dir));
        settingsServices.add(settings);
        VisibilityService visibility = new VisibilityService(config, permissions, settings, text);
        HotbarService hotbar = new HotbarService(config, text, actions, bridge, visibility);
        EventNode<PlayerEvent> node = EventNode.type("hotbar-test-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        menus.register(node);
        hotbar.register(node);
        return new Setup(hotbar, visibility, menus, settings, config);
    }

    private static Player join(Env env, Setup setup, Instance instance, String name) {
        UUID id = UUID.randomUUID();
        setup.settings().load(id);
        return env.createConnection(new GameProfile(id, name)).connect(instance, SPAWN);
    }

    /** The items are built in the background and put in the inventory on a later tick. */
    private static void awaitItem(Env env, Player player, int slot, Material material) {
        env.tickWhile(() -> player.getInventory().getItemStack(slot).material() != material, Duration.ofSeconds(5));
    }

    @Test
    void theBundledHotbarLoadsWithoutWarningsAndIsGivenOnJoin(Env env) throws Exception {
        Setup setup = start(env);
        assertEquals(List.of(), setup.config().current().warnings().stream()
                .filter(warning -> warning.startsWith("hotbar.yml") || warning.startsWith("menus.yml")).toList());
        Instance lobby = env.createFlatInstance();

        Player player = join(env, setup, lobby, "Steve");
        awaitItem(env, player, 0, Material.COMPASS);

        assertEquals(Material.COMPASS, player.getInventory().getItemStack(0).material());
        assertEquals(Material.NETHER_STAR, player.getInventory().getItemStack(1).material());
        assertEquals(Material.LIME_DYE, player.getInventory().getItemStack(7).material(), "visibility: all players");
        assertEquals(Material.CHEST, player.getInventory().getItemStack(8).material());
        assertEquals("games", player.getInventory().getItemStack(0).getTag(HotbarService.ITEM_TAG));
    }

    @Test
    void theItemsAreGivenAgainAfterChangingInstance(Env env) throws Exception {
        Setup setup = start(env);
        Instance first = env.createFlatInstance();
        Instance second = env.createFlatInstance();
        Player player = join(env, setup, first, "Steve");
        awaitItem(env, player, 0, Material.COMPASS);
        player.getInventory().clear();

        player.setInstance(second, SPAWN).join();
        awaitItem(env, player, 0, Material.COMPASS);

        assertEquals(Material.COMPASS, player.getInventory().getItemStack(0).material());
    }

    @Test
    void hotbarItemsCannotBeDroppedOrMoved(Env env) throws Exception {
        Setup setup = start(env);
        Player player = join(env, setup, env.createFlatInstance(), "Steve");
        awaitItem(env, player, 0, Material.COMPASS);
        ItemStack compass = player.getInventory().getItemStack(0);

        ItemDropEvent drop = new ItemDropEvent(player, compass);
        env.process().eventHandler().call(drop);
        InventoryPreClickEvent click = new InventoryPreClickEvent(player.getInventory(), player, new Click.Left(0));
        env.process().eventHandler().call(click);
        // Pressing a number key over another slot would swap the compass out of the hotbar.
        InventoryPreClickEvent swap = new InventoryPreClickEvent(player.getInventory(), player,
                new Click.HotbarSwap(0, 20));
        env.process().eventHandler().call(swap);

        assertTrue(drop.isCancelled());
        assertTrue(click.isCancelled());
        assertTrue(swap.isCancelled());
    }

    @Test
    void usingAnItemRunsItsActions(Env env) throws Exception {
        Setup setup = start(env);
        Player player = join(env, setup, env.createFlatInstance(), "Steve");
        awaitItem(env, player, 8, Material.CHEST);

        PlayerUseItemEvent use = new PlayerUseItemEvent(player, PlayerHand.MAIN, player.getInventory().getItemStack(8), 0);
        env.process().eventHandler().call(use);
        // Actions run in the background.
        env.tickWhile(() -> setup.menus().openMenu(player) == null, Duration.ofSeconds(5));

        assertTrue(use.isCancelled(), "a hotbar item is never used like a normal item");
        assertEquals("lobby", setup.menus().openMenu(player), "the chest opens the lobby menu");
    }

    @Test
    void theVisibilitySwitchHidesPlayersAndIsSaved(Env env) throws Exception {
        Setup setup = start(env);
        Instance lobby = env.createFlatInstance();
        Player viewer = join(env, setup, lobby, "Steve");
        Player other = join(env, setup, lobby, "Alex");
        Player staff = join(env, setup, lobby, "Staff");
        setup.settings().whenLoaded(viewer.getUuid()).join();
        env.tick();
        assertTrue(other.getViewers().contains(viewer), "everyone is shown at first");

        // all -> staff: only staff are shown.
        assertEquals(VisibilityMode.STAFF, setup.visibility().toggle(viewer));
        env.tick();
        assertFalse(other.getViewers().contains(viewer));
        assertTrue(staff.getViewers().contains(viewer));
        assertTrue(viewer.getViewers().contains(other), "the others still see the viewer");
        assertEquals("staff", setup.settings().get(viewer.getUuid()).visibility(), "the choice is saved");

        // Switching again right away is refused by the cooldown.
        assertEquals(null, setup.visibility().toggle(viewer));
    }

    @Test
    void aSavedChoiceIsAppliedWhenThePlayerJoins(Env env) throws Exception {
        Setup setup = start(env);
        Instance lobby = env.createFlatInstance();
        Player other = join(env, setup, lobby, "Alex");
        Player staff = join(env, setup, lobby, "Staff");
        UUID id = UUID.randomUUID();
        setup.settings().load(id);
        setup.settings().whenLoaded(id).join();
        setup.settings().update(id, current -> current.withVisibility("staff"));

        Player viewer = env.createConnection(new GameProfile(id, "Steve")).connect(lobby, SPAWN);
        env.tick();
        env.tick();

        assertFalse(other.getViewers().contains(viewer), "hidden by the viewer's own saved choice");
        assertTrue(staff.getViewers().contains(viewer));
        assertTrue(viewer.getViewers().contains(other), "the others still see the new player");
    }

    @Test
    void theSwitchShowsTheCurrentMode(Env env) throws Exception {
        Setup setup = start(env);
        Player viewer = join(env, setup, env.createFlatInstance(), "Steve");
        setup.settings().update(viewer.getUuid(), current -> current.withVisibility("none"));

        setup.hotbar().give(viewer);
        awaitItem(env, viewer, 7, Material.GRAY_DYE);

        assertEquals(Material.GRAY_DYE, viewer.getInventory().getItemStack(7).material(), "players hidden");
    }
}
