package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opens the bundled menu for fake players and checks items, conditions and click protection. */
@EnvTest
class MenuEnvTest {

    @TempDir
    Path dir;

    private record Setup(MenuService menus, ConfigManager config, Instance instance) {
    }

    private Setup start(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        Path main = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(main, Files.readString(main).replace("operators: []", "operators: [\"Admin\"]"));
        config.load();
        Instance instance = env.createFlatInstance();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, info(instance)));
        LobbyText text = new LobbyText(config, placeholders);
        ActionServices actions = new ActionServices(config, text, permissions, bridge);
        MenuService menus = new MenuService(() -> config.current().menus(), text, actions, bridge);
        actions.menus(menus);
        EventNode<PlayerEvent> node = EventNode.type("menu-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        menus.register(node);
        return new Setup(menus, config, instance);
    }

    private Player join(Env env, Instance instance, String name) {
        return env.createConnection(new GameProfile(UUID.randomUUID(), name)).connect(instance, new Pos(0, 41, 0));
    }

    private static String plain(ItemStack item) {
        return item.get(net.minestom.server.component.DataComponents.CUSTOM_NAME) == null ? ""
                : PlainTextComponentSerializer.plainText()
                        .serialize(item.get(net.minestom.server.component.DataComponents.CUSTOM_NAME));
    }

    @Test
    void bundledMenuLoadsWithoutWarnings(Env env) throws Exception {
        Setup setup = start(env);
        ConfigSnapshot snapshot = setup.config().current();

        assertEquals(List.of(), snapshot.warnings().stream()
                .filter(warning -> warning.startsWith("menus.yml")).toList());
        MenuDefinition lobby = snapshot.menus().menu("lobby");
        assertNotNull(lobby);
        assertEquals(3, lobby.rows());
        assertEquals(27, lobby.size());
        assertEquals(20, lobby.refreshTicks());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, lobby.fill());
        assertEquals(4, lobby.items().size());
        assertNull(snapshot.menus().menu("nothing"));
    }

    @Test
    void itemsFillSlotsAndPlaceholdersAreUsed(Env env) throws Exception {
        Setup setup = start(env);
        Player steve = join(env, setup.instance(), "Steve");

        setup.menus().open(steve, "lobby");

        Inventory inventory = (Inventory) steve.getOpenInventory();
        assertNotNull(inventory);
        assertEquals("lobby", setup.menus().openMenu(steve));
        assertEquals(Material.COMPASS, inventory.getItemStack(11).material());
        assertEquals("Spawn", plain(inventory.getItemStack(11)));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, inventory.getItemStack(0).material(), "empty slots are filled");
        // Steve is not an operator, so slot 15 holds the normal item, with the count filled in.
        assertEquals(Material.PAPER, inventory.getItemStack(15).material());
        List<net.kyori.adventure.text.Component> lore =
                inventory.getItemStack(15).get(net.minestom.server.component.DataComponents.LORE);
        assertNotNull(lore);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(lore.getFirst()).contains("Here: 1/"),
                lore::toString);
    }

    @Test
    void firstMatchingEntryWinsTheSlot(Env env) throws Exception {
        Setup setup = start(env);
        Player admin = join(env, setup.instance(), "Admin");

        setup.menus().open(admin, "lobby");

        Inventory inventory = (Inventory) admin.getOpenInventory();
        assertEquals(Material.REDSTONE_TORCH, inventory.getItemStack(15).material(), "staff see the staff item");
        assertEquals("Server status", plain(inventory.getItemStack(15)));
    }

    @Test
    void clicksCannotTakeItems(Env env) throws Exception {
        Setup setup = start(env);
        Player steve = join(env, setup.instance(), "Steve");
        setup.menus().open(steve, "lobby");
        Inventory inventory = (Inventory) steve.getOpenInventory();

        for (Click click : List.of(new Click.Left(11), new Click.Right(11), new Click.LeftShift(11),
                new Click.Double(11), new Click.Left(0), new Click.DropSlot(11, false))) {
            InventoryPreClickEvent event = new InventoryPreClickEvent(inventory, steve, click);
            net.minestom.server.event.EventDispatcher.call(event);
            assertTrue(event.isCancelled(), click.toString());
        }
        assertEquals(Material.COMPASS, inventory.getItemStack(11).material());
        assertEquals(ItemStack.AIR, inventory.getCursorItem(steve));
        assertTrue(steve.getInventory().getItemStack(0).isAir(), "nothing ended up in the player inventory");
    }

    @Test
    void unknownMenuIsIgnored(Env env) throws Exception {
        Setup setup = start(env);
        Player steve = join(env, setup.instance(), "Steve");

        setup.menus().open(steve, "does-not-exist");

        assertNull(setup.menus().openMenu(steve));
        assertFalse(steve.getOpenInventory() instanceof Inventory);
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
