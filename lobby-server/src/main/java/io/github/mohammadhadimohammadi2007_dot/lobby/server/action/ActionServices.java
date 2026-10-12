package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.ConnectEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.ServerConnectResultEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.entity.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

/**
 * What actions need from the rest of the lobby. Features that start later (server switching, menus, lobby
 * instances) plug themselves in with the setters; until then their actions tell the player they are not
 * available, so an action list never fails because of the order things start in.
 */
public final class ActionServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionServices.class);

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
    private volatile ProxyCommands proxyCommands;
    private final AtomicBoolean warnedNoProxy = new AtomicBoolean();

    public ActionServices(ConfigManager config, LobbyText text, PermissionService permissions, BridgeService bridge) {
        this.config = config;
        this.text = text;
        this.permissions = permissions;
        this.bridge = bridge;
        this.proxyCommands = this::noProxy;
        this.connector = new Connector() {
            @Override
            public CompletableFuture<Boolean> connect(Player player, String server) {
                player.sendMessage(text.message(MessageKey.CONNECT_NOT_AVAILABLE, player));
                ConnectEvents.fire(player, server, false, ServerConnectResultEvent.Outcome.NOT_AVAILABLE);
                return CompletableFuture.completedFuture(false);
            }

            @Override
            public CompletableFuture<Boolean> connectGroup(Player player, String group) {
                player.sendMessage(text.message(MessageKey.CONNECT_NOT_AVAILABLE, player));
                ConnectEvents.fire(player, group, true, ServerConnectResultEvent.Outcome.NOT_AVAILABLE);
                return CompletableFuture.completedFuture(false);
            }
        };
    }

    /** Without Velocity and the bridge, nothing can run commands on the proxy: the player is told so. */
    private CompletableFuture<Boolean> noProxy(Player player, String command, String source) {
        if (warnedNoProxy.compareAndSet(false, true)) {
            LOGGER.warn("'/{}' of {} is not a lobby command, so it would run on the proxy, but the lobby can only do"
                    + " that behind Velocity with the lobby bridge (mode: velocity, bridge.enabled in integrations.yml)."
                    + " Players are told it is not available.", command, source);
        }
        player.sendMessage(text.message(MessageKey.PROXY_COMMAND_NOT_AVAILABLE, player, Messages.text("command", command)));
        return CompletableFuture.completedFuture(false);
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

    /** Runs commands on the proxy as a player. */
    public ProxyCommands proxyCommands() {
        return proxyCommands;
    }

    public void proxyCommands(ProxyCommands value) {
        proxyCommands = Objects.requireNonNull(value);
    }

    public LobbySwitcher lobbies() {
        return lobbies;
    }

    public void lobbies(LobbySwitcher value) {
        lobbies = Objects.requireNonNull(value);
    }
}
