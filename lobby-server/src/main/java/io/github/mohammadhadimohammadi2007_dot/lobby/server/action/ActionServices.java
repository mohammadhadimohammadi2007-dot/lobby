package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.entity.Player;

import java.util.Objects;

/**
 * What actions need from the rest of the lobby. Features that start later (server switching, menus, lobby
 * instances) plug themselves in with the setters; until then their actions tell the player they are not
 * available, so an action list never fails because of the order things start in.
 */
public final class ActionServices {

    private final ConfigManager config;
    private final LobbyText text;
    private final PermissionService permissions;
    private final BridgeService bridge;
    private volatile Connector connector;
    private volatile MenuHandler menus = new MenuHandler() {
        @Override
        public void open(Player player, String menu) {
        }

        @Override
        public void close(Player player) {
            player.closeInventory();
        }
    };
    private volatile LobbySwitcher lobbies = (player, number) -> { };

    public ActionServices(ConfigManager config, LobbyText text, PermissionService permissions, BridgeService bridge) {
        this.config = config;
        this.text = text;
        this.permissions = permissions;
        this.bridge = bridge;
        this.connector = new Connector() {
            @Override
            public void connect(Player player, String server) {
                player.sendMessage(text.message(MessageKey.CONNECT_NOT_AVAILABLE, player));
            }

            @Override
            public void connectGroup(Player player, String group) {
                player.sendMessage(text.message(MessageKey.CONNECT_NOT_AVAILABLE, player));
            }
        };
    }

    public ConfigManager config() {
        return config;
    }

    public LobbyText text() {
        return text;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public BridgeService bridge() {
        return bridge;
    }

    public Connector connector() {
        return connector;
    }

    public void connector(Connector value) {
        connector = Objects.requireNonNull(value);
    }

    public MenuHandler menus() {
        return menus;
    }

    public void menus(MenuHandler value) {
        menus = Objects.requireNonNull(value);
    }

    public LobbySwitcher lobbies() {
        return lobbies;
    }

    public void lobbies(LobbySwitcher value) {
        lobbies = Objects.requireNonNull(value);
    }
}
