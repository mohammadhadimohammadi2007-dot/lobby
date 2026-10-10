package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.MenuHandler;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opens the menus from menus.yml and keeps them up to date.
 *
 * <p>Menus are read-only: every click is cancelled before anything moves, so players cannot take items out,
 * drop them, shift-click them into their inventory or swap them with the cursor. Only the slot's actions
 * run. Items are rebuilt while the menu is open, so a server list shows live player counts; the rebuilding
 * (placeholders, MiniMessage, conditions) runs on a virtual thread, and only changed slots are sent.
 *
 * <p>Besides the menus in the file, the lobby itself can add menus whose contents are only known while
 * running (the lobby selector) with {@link #addBuiltIn}.
 */
public final class MenuService implements MenuHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuService.class);
    private static final int CHECK_INTERVAL_TICKS = 5;

    private final Supplier<MenuConfig> config;
    private final LobbyText text;
    private final ActionServices actions;
    private final BridgeService bridge;
    private final Map<UUID, OpenMenu> open = new ConcurrentHashMap<>();
    private final Map<String, Function<Player, MenuDefinition>> builtIn = new ConcurrentHashMap<>();
    private long ticks;

    public MenuService(Supplier<MenuConfig> config, LobbyText text, ActionServices actions, BridgeService bridge) {
        this.config = config;
        this.text = text;
        this.actions = actions;
        this.bridge = bridge;
    }

    /** Starts listening for clicks and refreshing open menus. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(InventoryPreClickEvent.class, this::onClick);
        node.addListener(InventoryCloseEvent.class, event -> open.remove(event.getPlayer().getUuid()));
        node.addListener(PlayerDisconnectEvent.class, event -> open.remove(event.getPlayer().getUuid()));
        MinecraftServer.getSchedulerManager().buildTask(this::refreshOpenMenus)
                .repeat(TaskSchedule.tick(CHECK_INTERVAL_TICKS))
                .schedule();
    }

    /**
     * Adds a menu the lobby builds itself, for example the lobby selector, whose items depend on the
     * server's state and on who opens it. A menu with the same name in menus.yml wins, so server owners
     * can always replace it.
     */
    public void addBuiltIn(String name, Function<Player, MenuDefinition> builder) {
        builtIn.put(name.toLowerCase(Locale.ROOT), builder);
    }

    @Override
    public void open(Player player, String name) {
        MenuDefinition definition = definition(player, name);
        if (definition == null) {
            LOGGER.warn("Tried to open the menu '{}', which is not in menus.yml", name);
            return;
        }
        Inventory inventory = new Inventory(definition.inventoryType(), text.render(definition.title(), player));
        OpenMenu menu = new OpenMenu(definition, inventory, ticks);
        menu.show(contents(player, definition));
        open.put(player.getUuid(), menu);
        player.openInventory(inventory);
    }

    /** The menu from menus.yml, or a built-in one, or {@code null} if there is no such menu. */
    private @Nullable MenuDefinition definition(Player player, String name) {
        MenuDefinition fromFile = config.get().menu(name);
        if (fromFile != null) {
            return fromFile;
        }
        Function<Player, MenuDefinition> builder = builtIn.get(name.toLowerCase(Locale.ROOT));
        return builder == null ? null : builder.apply(player);
    }

    @Override
    public void close(Player player) {
        open.remove(player.getUuid());
        player.closeInventory();
    }

    /** The menu this player has open, or {@code null}. For tests. */
    public @Nullable String openMenu(Player player) {
        OpenMenu menu = open.get(player.getUuid());
        return menu == null ? null : menu.definition().name();
    }

    /** Every slot with the first item whose conditions hold for this player. Safe on any thread. */
    private MenuContents contents(Player player, MenuDefinition definition) {
        boolean legacy = bridge.capabilities(player).legacy();
        ItemStack filler = definition.fill() == null ? ItemStack.AIR : MenuItems.filler(definition.fill());
        ItemStack[] items = new ItemStack[definition.size()];
        Arrays.fill(items, filler);
        Map<Integer, MenuItem> itemsBySlot = new HashMap<>();
        for (MenuItem item : definition.items()) {
            if (!shown(item, player)) {
                continue;
            }
            for (int slot : item.slots()) {
                if (!itemsBySlot.containsKey(slot)) {
                    items[slot] = MenuItems.build(item, player, text, legacy);
                    itemsBySlot.put(slot, item);
                }
            }
        }
        return new MenuContents(items, Map.copyOf(itemsBySlot));
    }

    /** True if every {@code show-if} condition of the item holds for this player. */
    private boolean shown(MenuItem item, Player player) {
        for (MenuCondition condition : item.showIf()) {
            if (!condition.test(player, text.placeholders(), actions.permissions())) {
                return false;
            }
        }
        return true;
    }

    private void onClick(InventoryPreClickEvent event) {
        OpenMenu menu = open.get(event.getPlayer().getUuid());
        if (menu == null || event.getInventory() != menu.inventory()) {
            return;
        }
        // Cancel first, always: nothing in a menu may be taken, dropped, swapped or shift-clicked.
        event.setCancelled(true);
        ActionList clicked = menu.actionsAt(event.getSlot());
        if (clicked != null) {
            clicked.run(event.getPlayer(), actions, "menu '" + menu.definition().name() + "'");
        }
    }

    private void refreshOpenMenus() {
        ticks += CHECK_INTERVAL_TICKS;
        if (open.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, OpenMenu> entry : open.entrySet()) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(entry.getKey());
            OpenMenu menu = entry.getValue();
            if (player == null || !menu.isOpen(player)) {
                open.remove(entry.getKey());
                continue;
            }
            if (menu.dueForRefresh(ticks) && menu.startBuilding()) {
                rebuild(player, menu);
            }
        }
    }

    /** Builds the menu again on a virtual thread and shows it on the next tick, if it is still open. */
    private void rebuild(Player player, OpenMenu menu) {
        Async.supply(() -> contents(player, menu.definition())).whenComplete((contents, error) -> {
            if (error != null) {
                menu.built();
                LOGGER.error("Could not refresh the menu '{}' of {}", menu.definition().name(), player.getUsername(), error);
                return;
            }
            Async.onTickThread(() -> {
                try {
                    if (open.get(player.getUuid()) == menu && menu.isOpen(player)) {
                        menu.show(contents);
                    }
                } finally {
                    menu.built();
                }
            });
        });
    }
}
