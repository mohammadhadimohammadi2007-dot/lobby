package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.CloseMenuAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConnectAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.LobbyAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuDefinition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItem;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * The lobby selector: one item per lobby instance of this server, which a click moves the player to, and
 * after them the network's other lobby servers (the {@code lobbies} group of the proxy bridge), which a
 * click connects to.
 *
 * <p>It cannot live in menus.yml because the number of lobbies is only known while running, so it is
 * built fresh every time it is opened. The texts do come from messages.yml ({@code lobby-selector-*}),
 * where {@code <number>} is the lobby's number and {@code <players>} how many players are in it. The
 * player count is written as a placeholder, so it stays live while the menu is open.
 */
public final class LobbySelectorMenu {

    /** The name used by {@code open-menu: lobbies} and {@code /lobbies}. */
    public static final String NAME = "lobbies";

    /** A paper stack whose size is the lobby number, which old clients also show correctly. */
    private static final Material ITEM = Material.PAPER;
    /** Another lobby server; a different item, so it is clear it is a server, not an instance here. */
    private static final Material SERVER_ITEM = Material.BOOK;
    private static final Material OFFLINE_ITEM = Material.BARRIER;
    /** The bridge group that lists every lobby server of the network. */
    public static final String LOBBIES_GROUP = "lobbies";
    private static final String SERVER_TAG = "<server>";
    private static final int MAX_STACK = 64;
    /** The counts in the lore are rebuilt twice a second. */
    private static final int REFRESH_TICKS = 10;
    private static final int CLICK_COOLDOWN_MILLIS = 500;
    private static final String NUMBER_TAG = "<number>";
    private static final String PLAYERS_TAG = "<players>";

    private final ConfigManager config;
    private final LobbyInstances lobbies;
    private final NetworkState network;

    /** @param network what the bridge knows about the other servers; empty without a bridge */
    public LobbySelectorMenu(ConfigManager config, LobbyInstances lobbies, NetworkState network) {
        this.config = config;
        this.lobbies = lobbies;
        this.network = network;
    }

    /** Builds the menu as this player should see it. */
    public MenuDefinition build(Player player) {
        int count = lobbies.count();
        int current = lobbies.numberOf(player);
        List<MenuItem> items = new ArrayList<>(count);
        for (int number = 1; number <= count; number++) {
            items.add(item(number, number == current));
        }
        // The other lobby servers start on the next row, so the two kinds are not mixed up.
        int slot = count == 0 ? 0 : Math.ceilDiv(count, MenuDefinition.SLOTS_PER_ROW) * MenuDefinition.SLOTS_PER_ROW;
        int lastSlot = MenuDefinition.MAX_ROWS * MenuDefinition.SLOTS_PER_ROW;
        String self = config.current().config().server().name();
        for (String server : network.group(LOBBIES_GROUP)) {
            if (server.equalsIgnoreCase(self) || slot >= lastSlot) {
                continue;
            }
            items.add(serverItem(server, slot++));
        }
        int used = Math.max(1, items.stream().mapToInt(item -> item.slots().getFirst() + 1).max().orElse(1));
        int rows = Math.clamp(Math.ceilDiv(used, MenuDefinition.SLOTS_PER_ROW), 1, MenuDefinition.MAX_ROWS);
        return new MenuDefinition(NAME, message(MessageKey.LOBBY_SELECTOR_TITLE), rows, REFRESH_TICKS, null, items);
    }

    private MenuItem item(int number, boolean current) {
        List<String> lore = List.of(
                message(MessageKey.LOBBY_SELECTOR_PLAYERS).replace(PLAYERS_TAG, "%lobby_online_" + number + "%"),
                message(current ? MessageKey.LOBBY_SELECTOR_CURRENT : MessageKey.LOBBY_SELECTOR_CLICK));
        // Closing first keeps the "you are here" marker honest: the menu is rebuilt when it is reopened.
        ActionList actions = current ? ActionList.EMPTY
                : new ActionList(List.of(new CloseMenuAction(), new LobbyAction(number)), CLICK_COOLDOWN_MILLIS);
        return new MenuItem(List.of(number - 1), ITEM, Math.min(number, MAX_STACK),
                message(MessageKey.LOBBY_SELECTOR_NAME).replace(NUMBER_TAG, String.valueOf(number)),
                lore, current, List.of(), actions);
    }

    private MenuItem serverItem(String server, int slot) {
        boolean online = network.serverOnline(server);
        List<String> lore = online
                ? List.of(message(MessageKey.LOBBY_SELECTOR_SERVER_PLAYERS).replace(PLAYERS_TAG, "%bungee_" + server + "%"),
                        message(MessageKey.LOBBY_SELECTOR_CLICK))
                : List.of(message(MessageKey.LOBBY_SELECTOR_SERVER_OFFLINE));
        ActionList actions = online
                ? new ActionList(List.of(new CloseMenuAction(), new ConnectAction(server, false)), CLICK_COOLDOWN_MILLIS)
                : ActionList.EMPTY;
        return new MenuItem(List.of(slot), online ? SERVER_ITEM : OFFLINE_ITEM, 1,
                message(MessageKey.LOBBY_SELECTOR_SERVER_NAME).replace(SERVER_TAG, server), lore, false, List.of(),
                actions);
    }

    private String message(MessageKey key) {
        return config.current().messages().template(key);
    }
}
