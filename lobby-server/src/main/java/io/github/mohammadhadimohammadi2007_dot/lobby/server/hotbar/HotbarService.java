package io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItems;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hotbar items from {@code hotbar.yml}: given on join and whenever a player changes lobby instance,
 * locked in place, and running their actions when right-clicked. (A left click cannot be told apart
 * reliably: clients swing their arm on right-clicks too.)
 *
 * <p>Each item carries a tag with its name, so it is recognised wherever it is. Players can never move,
 * drop, swap to the off hand or place a tagged item. A right-click on a block can reach the server as two
 * packets on some clients, so one use within {@link #DOUBLE_USE_MILLIS} counts once.
 */
public final class HotbarService {

    /** The name of the hotbar item an item stack is. */
    public static final Tag<String> ITEM_TAG = Tag.String("lobby_hotbar");
    private static final long DOUBLE_USE_MILLIS = 150;
    private static final Logger LOGGER = LoggerFactory.getLogger(HotbarService.class);

    private final ConfigManager config;
    private final LobbyText text;
    private final ActionServices actions;
    private final BridgeService bridge;
    private final VisibilityService visibility;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    /** The latest {@link #give} per player; an older one that finishes later is dropped. */
    private final Map<UUID, Long> gives = new ConcurrentHashMap<>();

    public HotbarService(ConfigManager config, LobbyText text, ActionServices actions, BridgeService bridge,
                         VisibilityService visibility) {
        this.config = config;
        this.text = text;
        this.actions = actions;
        this.bridge = bridge;
        this.visibility = visibility;
    }

    public void register(EventNode<PlayerEvent> node) {
        node.addListener(AsyncPlayerConfigurationEvent.class, event -> visibility.applyRules(event.getPlayer()));
        node.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            if (event.isFirstSpawn()) {
                // The switch item shows the saved choice once it has been read.
                visibility.watch(player, () -> give(player));
            }
            // Every spawn, which includes moving to another lobby instance.
            give(player);
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            lastUse.remove(event.getPlayer().getUuid());
            gives.remove(event.getPlayer().getUuid());
            visibility.forget(event.getPlayer().getUuid());
        });
        node.addListener(InventoryPreClickEvent.class, this::onClick);
        node.addListener(ItemDropEvent.class, event -> {
            if (isHotbarItem(event.getItemStack())) {
                event.setCancelled(true);
            }
        });
        node.addListener(PlayerSwapItemEvent.class, event -> {
            if (isHotbarItem(event.getMainHandItem()) || isHotbarItem(event.getOffHandItem())) {
                event.setCancelled(true);
            }
        });
        node.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (isHotbarItem(held(event.getPlayer()))) {
                event.setCancelled(true);
            }
        });
        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getHand() == PlayerHand.MAIN && isHotbarItem(event.getItemStack())) {
                // A hotbar item is never eaten, thrown or drawn like a bow.
                event.setCancelled(true);
                use(event.getPlayer(), event.getItemStack());
            }
        });
        node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
            if (event.getHand() == PlayerHand.MAIN && isHotbarItem(event.getItemStack())) {
                use(event.getPlayer(), event.getItemStack());
            }
        });
    }

    /**
     * Gives a player their hotbar items (the ones they have the permission for). The items are built on a
     * virtual thread, since placeholders and MiniMessage are slow next to a tick, and put in the inventory
     * at the start of the next tick.
     */
    public void give(Player player) {
        HotbarConfig hotbar = config.current().hotbar();
        if (!hotbar.enabled()) {
            return;
        }
        long request = gives.merge(player.getUuid(), 1L, Long::sum);
        Async.supply(() -> build(player, hotbar)).whenComplete((items, error) -> {
            if (error != null) {
                LOGGER.error("Could not build the hotbar items of {}", player.getUsername(), error);
                return;
            }
            Async.onTickThread(() -> {
                if (player.isOnline() && gives.getOrDefault(player.getUuid(), 0L) == request) {
                    apply(player, hotbar, items);
                }
            });
        });
    }

    /** The items {@code player} gets, by slot. */
    private Map<Integer, ItemStack> build(Player player, HotbarConfig hotbar) {
        boolean legacy = bridge.capabilities(player).legacy();
        VisibilityMode mode = visibility.mode(player);
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        for (HotbarConfig.Item item : hotbar.items()) {
            if (!item.permission().isEmpty() && !actions.permissions().hasPermission(player, item.permission())) {
                continue;
            }
            items.put(item.slot(), MenuItems.build(item.look(mode), player, text, legacy).withTag(ITEM_TAG, item.id()));
        }
        return items;
    }

    private static void apply(Player player, HotbarConfig hotbar, Map<Integer, ItemStack> items) {
        PlayerInventory inventory = player.getInventory();
        if (hotbar.clearInventory()) {
            inventory.clear();
        } else {
            // Items a previous config gave in other slots must not stay behind.
            for (int slot = 0; slot < HotbarConfig.HOTBAR_SLOTS; slot++) {
                if (isHotbarItem(inventory.getItemStack(slot))) {
                    inventory.setItemStack(slot, ItemStack.AIR);
                }
            }
        }
        items.forEach(inventory::setItemStack);
    }

    /** Gives every online player their items again (after a reload). */
    public void giveAll() {
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(this::give);
    }

    private void use(Player player, ItemStack stack) {
        HotbarConfig.Item item = item(stack);
        if (item == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.put(player.getUuid(), now);
        if (last != null && now - last < DOUBLE_USE_MILLIS) {
            return;
        }
        if (item.visibilityToggle()) {
            if (visibility.toggle(player) != null) {
                give(player);
            }
            return;
        }
        item.actions().run(player, actions, "hotbar item '" + item.id() + "'");
    }

    /** Cancels every click that would move a hotbar item in the player's own inventory. */
    private void onClick(InventoryPreClickEvent event) {
        if (!(event.getInventory() instanceof PlayerInventory inventory)) {
            return;
        }
        boolean locked = isHotbarItem(event.getClickedItem()) || isHotbarItem(inventory.getCursorItem());
        if (event.getClick() instanceof Click.HotbarSwap swap) {
            locked |= isHotbarItem(inventory.getItemStack(swap.hotbarSlot()));
        }
        if (event.getClick() instanceof Click.OffhandSwap) {
            locked = true;
        }
        if (locked) {
            event.setCancelled(true);
        }
    }

    private @Nullable HotbarConfig.Item item(ItemStack stack) {
        String id = stack.getTag(ITEM_TAG);
        if (id == null) {
            return null;
        }
        for (HotbarConfig.Item item : config.current().hotbar().items()) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        return null;
    }

    static boolean isHotbarItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isAir() && stack.getTag(ITEM_TAG) != null;
    }

    private static ItemStack held(Player player) {
        return player.getInventory().getItemStack(player.getHeldSlot());
    }
}
