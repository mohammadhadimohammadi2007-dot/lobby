package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeProtocol;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Velocity plugin that tells Minestom lobbies about the network: each player's real client version
 * and, every few seconds, how many players are on each server.
 */
@Plugin(
        id = "lobby-bridge",
        name = "Lobby Bridge",
        version = "0.1.0",
        description = "Sends client versions and player counts to Minestom lobby servers.",
        url = "https://github.com/mohammadhadimohammadi2007-dot",
        authors = {"mohammadhadimohammadi2007-dot"},
        dependencies = {@Dependency(id = "viaversion", optional = true)}
)
public final class LobbyBridgePlugin {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(BridgeProtocol.CHANNEL);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private BridgeConfig config;
    private boolean viaVersion;

    @Inject
    public LobbyBridgePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            config = BridgeConfig.load(dataDir, logger);
        } catch (IOException | RuntimeException e) {
            logger.error("Could not load config.toml, the lobby bridge is disabled: {}", e.getMessage());
            return;
        }
        for (String lobby : config.lobbyServers()) {
            if (proxy.getServer(lobby).isEmpty()) {
                logger.warn("Lobby server '{}' from config.toml is not in velocity.toml.", lobby);
            }
        }

        viaVersion = proxy.getPluginManager().isLoaded("viaversion");
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(this, ServerPostConnectEvent.class, this::onServerConnected);
        proxy.getEventManager().register(this, PluginMessageEvent.class, this::onPluginMessage);
        proxy.getScheduler().buildTask(this, this::pushSnapshot)
                .repeat(config.updateIntervalSeconds(), TimeUnit.SECONDS)
                .schedule();

        logger.info("Lobby bridge ready: lobbies {}, {} group(s), update every {}s, client versions from {}.",
                config.lobbyServers(), config.groups().size(), config.updateIntervalSeconds(),
                viaVersion ? "ViaVersion" : "Velocity");
    }

    /** When a player arrives on a lobby, send their real client version and a fresh snapshot. */
    private void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer()
                .filter(connection -> isLobby(connection.getServerInfo().getName()))
                .ifPresent(connection -> {
                    send(connection, new BridgeMessage.ClientVersion(player.getUniqueId(), clientProtocol(player)));
                    send(connection, snapshot());
                });
    }

    /**
     * Drops every {@code lobby:bridge} message that is not ours. Without this a player could forge
     * messages (for example a fake client version) and Velocity would forward them to the lobby.
     */
    private void onPluginMessage(PluginMessageEvent event) {
        if (CHANNEL.equals(event.getIdentifier())) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    /** Sends the current snapshot to every lobby that has players (messages travel through a player). */
    private void pushSnapshot() {
        byte[] data = BridgeCodec.encode(snapshot());
        for (String name : config.lobbyServers()) {
            proxy.getServer(name)
                    .filter(server -> !server.getPlayersConnected().isEmpty())
                    .ifPresent(server -> server.sendPluginMessage(CHANNEL, data));
        }
    }

    private BridgeMessage.NetworkSnapshot snapshot() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RegisteredServer server : proxy.getAllServers()) {
            counts.put(server.getServerInfo().getName(), server.getPlayersConnected().size());
        }
        return new BridgeMessage.NetworkSnapshot(proxy.getPlayerCount(), counts, config.groups());
    }

    private int clientProtocol(Player player) {
        if (viaVersion) {
            try {
                return ViaVersionHook.clientProtocol(player.getUniqueId());
            } catch (RuntimeException e) {
                logger.debug("ViaVersion did not know {}, using Velocity's version", player.getUsername(), e);
            }
        }
        return player.getProtocolVersion().getProtocol();
    }

    private void send(ServerConnection connection, BridgeMessage message) {
        try {
            connection.sendPluginMessage(CHANNEL, BridgeCodec.encode(message));
        } catch (IllegalArgumentException e) {
            logger.warn("Could not send bridge message: {}", e.getMessage());
        }
    }

    private boolean isLobby(String serverName) {
        return config.lobbyServers().contains(serverName);
    }
}
