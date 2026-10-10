package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConnectAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.RandomAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.BarsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.DisplayConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.SidebarConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.TieredText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuCondition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuDefinition;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItem;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcTrigger;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every group and server of the network that the lobby's configuration and data name: actions
 * ({@code connect}, {@code connect_group}, also inside {@code random:}) and placeholders
 * ({@code %group_online_<group>%}, {@code %group_status_<group>%}, {@code %group_max_<group>%},
 * {@code %bungee_<server>%}) in menus, hotbar items, NPCs, portals, holograms and the display texts.
 *
 * <p>Names that are themselves placeholders (a {@code connect: %player_last_server%}) are left out: they
 * are only known when the action runs.
 */
public final class NetworkReferences {

    private static final Pattern GROUP_PLACEHOLDER = Pattern.compile("%group_(?:online|status|max)_([^%\\s]+)%");
    private static final Pattern SERVER_PLACEHOLDER = Pattern.compile("%bungee_([^%\\s]+)%");
    private static final String NETWORK_TOTAL = "total";

    private final List<NetworkReference> found = new ArrayList<>();

    /** Everything the given configuration and data name. */
    public static List<NetworkReference> collect(ConfigSnapshot config, List<HologramData> holograms, List<NpcData> npcs,
                                                 List<Portal> portals) {
        NetworkReferences references = new NetworkReferences();
        references.menus(config.menus().menus());
        references.hotbar(config.hotbar());
        references.display(config.display());
        for (HologramData hologram : holograms) {
            String where = "hologram " + hologram.name();
            hologram.frames().forEach(frame -> frame.forEach(line -> references.text(line, where)));
            references.actions(hologram.actions(), where);
        }
        for (NpcData npc : npcs) {
            String where = "NPC " + npc.name();
            npc.nameTag().frames().forEach(frame -> frame.forEach(line -> references.text(line, where + " (name)")));
            for (Map.Entry<NpcTrigger, NpcData.Actions> entry : npc.allActions().entrySet()) {
                references.actions(entry.getValue().parsed(), where + " (" + entry.getKey().commandName() + ")");
            }
        }
        for (Portal portal : portals) {
            references.actions(portal.actions(), "portal " + portal.name());
        }
        return List.copyOf(references.found);
    }

    private void menus(Map<String, MenuDefinition> menus) {
        for (MenuDefinition menu : menus.values()) {
            String menuWhere = "menus.yml: " + menu.name();
            text(menu.title(), menuWhere + " (title)");
            for (MenuItem item : menu.items()) {
                String where = menuWhere + " > slot " + item.slots().getFirst();
                item(item, where);
                for (MenuCondition condition : item.showIf()) {
                    if (condition instanceof MenuCondition.Compare compare) {
                        text(compare.left(), where);
                        text(compare.right(), where);
                    }
                }
            }
        }
    }

    private void hotbar(HotbarConfig hotbar) {
        for (HotbarConfig.Item item : hotbar.items()) {
            String where = "hotbar.yml: " + item.id();
            item(item.look(), where);
            item.modeLooks().values().forEach(look -> item(look, where));
            actions(item.actions(), where);
        }
    }

    private void item(MenuItem item, String where) {
        text(item.name(), where);
        item.lore().forEach(line -> text(line, where));
        actions(item.actions(), where);
    }

    private void display(DisplayConfig display) {
        for (SidebarConfig.Board board : display.sidebar().boards()) {
            String where = "display.yml: scoreboard " + board.name();
            board.title().forEach(title -> tiered(title, where));
            board.lines().forEach(line -> tiered(line.text(), where));
            if (board.legacyLines() != null) {
                board.legacyLines().forEach(line -> tiered(line.text(), where));
            }
        }
        tiered(display.tab().header(), "display.yml: tab header");
        tiered(display.tab().footer(), "display.yml: tab footer");
        for (BarsConfig.Message message : display.bars().bossBar().messages()) {
            tiered(message.text(), "display.yml: bossbar");
        }
        for (BarsConfig.Message message : display.bars().actionBar().messages()) {
            tiered(message.text(), "display.yml: action-bar");
        }
    }

    private void tiered(TieredText text, String where) {
        text(text.modern(), where);
        if (text.legacy() != null) {
            text(text.legacy(), where);
        }
    }

    /** Group and server placeholders in a text. */
    void text(String text, String where) {
        if (text == null || text.indexOf('%') < 0) {
            return;
        }
        Matcher groups = GROUP_PLACEHOLDER.matcher(text);
        while (groups.find()) {
            add(NetworkReference.Kind.GROUP, groups.group(1), where);
        }
        Matcher servers = SERVER_PLACEHOLDER.matcher(text);
        while (servers.find()) {
            if (!servers.group(1).equals(NETWORK_TOTAL)) {
                add(NetworkReference.Kind.SERVER, servers.group(1), where);
            }
        }
    }

    private void actions(ActionList list, String where) {
        actions(list.actions(), where);
    }

    private void actions(List<Action> actions, String where) {
        for (Action action : actions) {
            switch (action) {
                case ConnectAction connect -> add(connect.group() ? NetworkReference.Kind.GROUP
                        : NetworkReference.Kind.SERVER, connect.target(), where);
                case RandomAction random -> actions(random.options(), where);
                default -> {
                }
            }
        }
    }

    private void add(NetworkReference.Kind kind, String name, String where) {
        if (name.isBlank() || name.indexOf('%') >= 0) {
            return;
        }
        found.add(new NetworkReference(kind, name.strip(), where));
    }
}
