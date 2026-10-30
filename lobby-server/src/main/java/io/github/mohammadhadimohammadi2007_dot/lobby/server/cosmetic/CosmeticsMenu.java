package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuDefinition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItems;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The cosmetics menu ({@code open_menu: cosmetics} and {@code /cosmetics}): a page with the three kinds, the
 * "other players' cosmetics" switch and "take everything off", and one page per kind listing its cosmetics.
 *
 * <p>Left-click puts a cosmetic on or takes it off; right-click previews a trail or gadget for the player only.
 * Cosmetics the player's client cannot show are not listed; locked ones are listed as locked unless
 * {@code show-locked: false}. Like every menu, nothing can be taken out: every click is cancelled.
 */
public final class CosmeticsMenu {

    /** The menu name for {@code open_menu}. */
    public static final String NAME = "cosmetics";

    private static final Logger LOGGER = LoggerFactory.getLogger(CosmeticsMenu.class);
    private static final long CLICK_COOLDOWN_MILLIS = 250;
    private static final int MAIN_ROWS = 3;
    private static final Map<CosmeticType, Integer> CATEGORY_SLOTS = Map.of(CosmeticType.TRAIL, 10,
            CosmeticType.HAT, 12, CosmeticType.GADGET, 14);
    private static final int HIDE_OTHERS_SLOT = 16;
    private static final int CLEAR_ALL_SLOT = 22;
    private static final Map<CosmeticType, Material> CATEGORY_ICONS = Map.of(CosmeticType.TRAIL, Material.BLAZE_POWDER,
            CosmeticType.HAT, Material.GOLDEN_HELMET, CosmeticType.GADGET, Material.FIREWORK_ROCKET);
    private static final Map<CosmeticType, MessageKey> CATEGORY_NAMES = Map.of(CosmeticType.TRAIL,
            MessageKey.COSMETICS_TRAILS, CosmeticType.HAT, MessageKey.COSMETICS_HATS, CosmeticType.GADGET,
            MessageKey.COSMETICS_GADGETS);

    /** What a slot does when clicked. */
    private sealed interface Button {
        record Category(CosmeticType type) implements Button { }
        record HideOthers() implements Button { }
        record ClearAll() implements Button { }
        record Back() implements Button { }
        record TakeOff(CosmeticType type) implements Button { }
        record Pick(Cosmetic cosmetic) implements Button { }
    }

    /** A built page: the items and what each slot does. */
    private record Page(@Nullable CosmeticType type, int rows, ItemStack[] items, Map<Integer, Button> buttons) {
    }

    /** The page a player has open. */
    private record Open(Inventory inventory, Page page) {
    }

    private final CosmeticsService cosmetics;
    private final LobbyText text;
    private final Predicate<Player> legacyClient;
    private final Map<UUID, Open> open = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    /** @param legacyClient true for clients that need the 16 old colors in item names */
    public CosmeticsMenu(CosmeticsService cosmetics, LobbyText text, Predicate<Player> legacyClient) {
        this.cosmetics = cosmetics;
        this.text = text;
        this.legacyClient = legacyClient;
    }

    public void register(EventNode<PlayerEvent> node) {
        node.addListener(InventoryPreClickEvent.class, this::onClick);
        node.addListener(InventoryCloseEvent.class, event -> {
            Open current = open.get(event.getPlayer().getUuid());
            if (current != null && current.inventory() == event.getInventory()) {
                open.remove(event.getPlayer().getUuid());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            open.remove(event.getPlayer().getUuid());
            lastClick.remove(event.getPlayer().getUuid());
        });
    }

    /** Opens the first page. Any thread; the page is built in the background and opened on the tick thread. */
    public void open(Player player) {
        if (!cosmetics.config().enabled()) {
            player.sendMessage(text.message(MessageKey.COSMETICS_DISABLED, player));
            return;
        }
        show(player, null);
    }

    /** Builds a page in the background and opens it (or updates it in place if that page is open). */
    private void show(Player player, @Nullable CosmeticType type) {
        Async.supply(() -> type == null ? mainPage(player) : typePage(player, type)).whenComplete((page, error) -> {
            if (error != null) {
                LOGGER.error("Could not build the cosmetics menu of {}", player.getUsername(), error);
                return;
            }
            Async.onTickThread(() -> {
                if (!player.isOnline()) {
                    return;
                }
                Open current = open.get(player.getUuid());
                if (current != null && player.getOpenInventory() == current.inventory()
                        && current.page().type() == page.type() && current.page().rows() == page.rows()) {
                    for (int slot = 0; slot < page.items().length; slot++) {
                        if (!current.inventory().getItemStack(slot).equals(page.items()[slot])) {
                            current.inventory().setItemStack(slot, page.items()[slot]);
                        }
                    }
                    open.put(player.getUuid(), new Open(current.inventory(), page));
                    return;
                }
                Component title = page.type() == null ? text.message(MessageKey.COSMETICS_MENU_TITLE, player)
                        : text.message(MessageKey.COSMETICS_PAGE_TITLE, player,
                        Placeholder.component("kind", text.message(CATEGORY_NAMES.get(page.type()), player)));
                Inventory inventory = new Inventory(inventoryType(page.rows()), title);
                for (int slot = 0; slot < page.items().length; slot++) {
                    inventory.setItemStack(slot, page.items()[slot]);
                }
                open.put(player.getUuid(), new Open(inventory, page));
                player.openInventory(inventory);
            });
        });
    }

    private Page mainPage(Player player) {
        boolean legacy = legacyClient.test(player);
        ItemStack[] items = empty(MAIN_ROWS);
        Map<Integer, Button> buttons = new HashMap<>();
        for (CosmeticType type : CosmeticType.values()) {
            int slot = CATEGORY_SLOTS.get(type);
            Cosmetic using = cosmetics.equipped(player, type);
            String current = using == null ? template(MessageKey.COSMETICS_NOTHING) : using.name();
            List<String> lore = List.of(
                    template(MessageKey.COSMETICS_KIND_UNLOCKED)
                            .replace("<unlocked>", String.valueOf(cosmetics.unlockedCount(player, type)))
                            .replace("<total>", String.valueOf(visible(player, type, true).size())),
                    template(MessageKey.COSMETICS_KIND_USING).replace("<current>", current));
            items[slot] = MenuItems.build(ItemStack.of(CATEGORY_ICONS.get(type)), template(CATEGORY_NAMES.get(type)),
                    lore, using != null, player, text, legacy);
            buttons.put(slot, new Button.Category(type));
        }
        boolean hidden = cosmetics.choices(player).hideOthers();
        items[HIDE_OTHERS_SLOT] = MenuItems.build(ItemStack.of(hidden ? Material.ENDER_PEARL : Material.ENDER_EYE),
                template(hidden ? MessageKey.COSMETICS_OTHERS_HIDDEN : MessageKey.COSMETICS_OTHERS_SHOWN),
                List.of(template(MessageKey.COSMETICS_OTHERS_LORE)), false, player, text, legacy);
        buttons.put(HIDE_OTHERS_SLOT, new Button.HideOthers());
        items[CLEAR_ALL_SLOT] = MenuItems.build(ItemStack.of(Material.BARRIER), template(MessageKey.COSMETICS_CLEAR_ALL),
                List.of(), false, player, text, legacy);
        buttons.put(CLEAR_ALL_SLOT, new Button.ClearAll());
        return new Page(null, MAIN_ROWS, items, buttons);
    }

    private Page typePage(Player player, CosmeticType type) {
        boolean legacy = legacyClient.test(player);
        List<Cosmetic> shown = visible(player, type, cosmetics.config().showLocked());
        int rows = Math.clamp(Math.ceilDiv(shown.size(), MenuDefinition.SLOTS_PER_ROW) + 1, 2, MenuDefinition.MAX_ROWS);
        ItemStack[] items = empty(rows);
        Map<Integer, Button> buttons = new HashMap<>();
        Cosmetic using = cosmetics.equipped(player, type);
        for (int i = 0; i < shown.size(); i++) {
            Cosmetic cosmetic = shown.get(i);
            boolean equipped = using != null && using.id().equals(cosmetic.id());
            List<String> lore = new ArrayList<>(cosmetic.lore());
            if (!lore.isEmpty()) {
                lore.add("");
            }
            lore.add(template(equipped ? MessageKey.COSMETICS_STATUS_USING
                    : cosmetics.unlocked(player, cosmetic) ? MessageKey.COSMETICS_STATUS_AVAILABLE
                    : MessageKey.COSMETICS_STATUS_LOCKED));
            if (cosmetic.previewable()) {
                lore.add(template(MessageKey.COSMETICS_STATUS_PREVIEW));
            }
            items[i] = MenuItems.build(cosmetic.iconItem(), cosmetic.name(), lore, equipped, player, text, legacy);
            buttons.put(i, new Button.Pick(cosmetic));
        }
        int lastRow = (rows - 1) * MenuDefinition.SLOTS_PER_ROW;
        items[lastRow] = MenuItems.build(ItemStack.of(Material.ARROW), template(MessageKey.COSMETICS_BACK), List.of(),
                false, player, text, legacy);
        buttons.put(lastRow, new Button.Back());
        if (using != null) {
            int slot = lastRow + MenuDefinition.SLOTS_PER_ROW / 2;
            items[slot] = MenuItems.build(ItemStack.of(Material.BARRIER), template(MessageKey.COSMETICS_TAKE_OFF),
                    List.of(), false, player, text, legacy);
            buttons.put(slot, new Button.TakeOff(type));
        }
        return new Page(type, rows, items, buttons);
    }

    /** The cosmetics of a type the player's client can show; locked ones only if {@code withLocked}. */
    private List<Cosmetic> visible(Player player, CosmeticType type, boolean withLocked) {
        return cosmetics.config().of(type).stream()
                .filter(cosmetic -> cosmetics.supported(player, cosmetic))
                .filter(cosmetic -> withLocked || cosmetics.unlocked(player, cosmetic))
                .toList();
    }

    private void onClick(InventoryPreClickEvent event) {
        Player player = event.getPlayer();
        Open current = open.get(player.getUuid());
        if (current == null || player.getOpenInventory() != current.inventory()) {
            return;
        }
        // Nothing in the menu, nor in the player's own inventory below it, moves while it is open.
        event.setCancelled(true);
        if (event.getInventory() != current.inventory()) {
            return;
        }
        Button button = current.page().buttons().get(event.getSlot());
        if (button == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUuid(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MILLIS) {
            return;
        }
        boolean right = event.getClick() instanceof Click.Right || event.getClick() instanceof Click.RightShift;
        switch (button) {
            case Button.Category category -> show(player, category.type());
            case Button.Back ignored -> show(player, null);
            case Button.HideOthers ignored -> {
                boolean hidden = cosmetics.toggleHideOthers(player);
                player.sendMessage(text.message(hidden ? MessageKey.COSMETICS_OTHERS_NOW_HIDDEN
                        : MessageKey.COSMETICS_OTHERS_NOW_SHOWN, player));
                show(player, null);
            }
            case Button.ClearAll ignored -> {
                cosmetics.clear(player);
                player.sendMessage(text.message(MessageKey.COSMETICS_CLEARED, player));
                show(player, null);
            }
            case Button.TakeOff takeOff -> {
                Cosmetic using = cosmetics.equipped(player, takeOff.type());
                if (using != null && cosmetics.unequip(player, takeOff.type()) == CosmeticsService.Change.DONE) {
                    player.sendMessage(text.message(MessageKey.COSMETICS_REMOVED, player, name(using, player)));
                }
                show(player, takeOff.type());
            }
            case Button.Pick pick -> pick(player, pick.cosmetic(), right);
        }
    }

    private void pick(Player player, Cosmetic cosmetic, boolean preview) {
        if (preview) {
            if (cosmetic.previewable()) {
                player.closeInventory();
                cosmetics.preview(player, cosmetic);
                player.sendMessage(text.message(MessageKey.COSMETICS_PREVIEW, player, name(cosmetic, player),
                        Messages.text("seconds", cosmetics.config().previewMillis() / 1000)));
            }
            return;
        }
        Cosmetic using = cosmetics.equipped(player, cosmetic.type());
        if (using != null && using.id().equals(cosmetic.id())) {
            if (cosmetics.unequip(player, cosmetic.type()) == CosmeticsService.Change.DONE) {
                player.sendMessage(text.message(MessageKey.COSMETICS_REMOVED, player, name(cosmetic, player)));
            }
        } else {
            switch (cosmetics.equip(player, cosmetic)) {
                case DONE -> player.sendMessage(text.message(MessageKey.COSMETICS_EQUIPPED, player, name(cosmetic, player)));
                case LOCKED, UNSUPPORTED, UNKNOWN -> player.sendMessage(text.message(MessageKey.COSMETICS_LOCKED, player,
                        name(cosmetic, player)));
                case DISABLED -> player.sendMessage(text.message(MessageKey.COSMETICS_DISABLED, player));
                case CANCELLED -> { }
            }
        }
        show(player, cosmetic.type());
    }

    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver name(Cosmetic cosmetic, Player player) {
        return Placeholder.component("name", cosmetics.name(cosmetic, player));
    }

    private String template(MessageKey key) {
        return cosmetics.messages().template(key);
    }

    private static ItemStack[] empty(int rows) {
        ItemStack[] items = new ItemStack[rows * MenuDefinition.SLOTS_PER_ROW];
        java.util.Arrays.fill(items, ItemStack.AIR);
        return items;
    }

    private static InventoryType inventoryType(int rows) {
        return switch (rows) {
            case 1 -> InventoryType.CHEST_1_ROW;
            case 2 -> InventoryType.CHEST_2_ROW;
            case 3 -> InventoryType.CHEST_3_ROW;
            case 4 -> InventoryType.CHEST_4_ROW;
            case 5 -> InventoryType.CHEST_5_ROW;
            default -> InventoryType.CHEST_6_ROW;
        };
    }

    /** The page a player has open (null for none, the type for a type page), for tests. */
    @Nullable String openPage(Player player) {
        Open current = open.get(player.getUuid());
        if (current == null || player.getOpenInventory() != current.inventory()) {
            return null;
        }
        return current.page().type() == null ? "main" : current.page().type().configName();
    }
}
