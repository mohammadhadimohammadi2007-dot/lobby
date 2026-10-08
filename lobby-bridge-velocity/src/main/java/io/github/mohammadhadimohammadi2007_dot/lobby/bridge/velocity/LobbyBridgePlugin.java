package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
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
import com.velocitypowered.api.proxy.server.ServerPing;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeFormatException;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage.ConnectResult.Outcome;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeProtocol;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Velocity plugin that connects Minestom lobbies to the network:
 * <ul>
 *   <li>tells lobbies each player's real client version and, every few seconds, player counts and
 *       server status (online/offline) of every server;</li>
 *   <li>relays global and staff chat from one lobby to the others;</li>
 *   <li>runs allowlisted console commands requested by lobbies (chat auto-mute);</li>
 *   <li>sends players to other servers, or to the best server of a group, when a lobby asks;</li>
 *   <li>tells lobbies when a player's skin changed on the proxy (SkinsRestorer's /skin).</li>
 * </ul>
 */
@Plugin(
        id = "lobby-bridge",
        name = "Lobby Bridge",
        version = "0.3.0",
        description = "Connects Minestom lobby servers to the network: client versions, player counts, chat relay.",
        url = "https://github.com/mohammadhadimohammadi2007-dot/lobby",
        authors = {"mohammadhadimohammadi2007-dot"},
        dependencies = {@Dependency(id = "viaversion", optional = true)}
)
public final class LobbyBridgePlugin {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(BridgeProtocol.CHANNEL);
    private static final long PING_TIMEOUT_SECONDS = 3;
    /** How often player skins are compared with what the lobbies were last told. */
    private static final long SKIN_CHECK_SECONDS = 2;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private final Map<String, BridgeMessage.ServerStatus.Status> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, SkinsRestorerHook.Skin> skins = new ConcurrentHashMap<>();
    private BridgeConfig config;
    private CommandGate commandGate;
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
        commandGate = new CommandGate(config.allowedCommands());

        viaVersion = proxy.getPluginManager().isLoaded("viaversion");
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(this, ServerPostConnectEvent.class, this::onServerConnected);
        proxy.getEventManager().register(this, PluginMessageEvent.class, this::onPluginMessage);
        proxy.getScheduler().buildTask(this, this::pushSnapshot)
                .repeat(config.updateIntervalSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, this::pingServers)
                .repeat(config.statusIntervalSeconds(), TimeUnit.SECONDS)
                .schedule();
        proxy.getScheduler().buildTask(this, this::checkSkins)
                .repeat(SKIN_CHECK_SECONDS, TimeUnit.SECONDS)
                .schedule();
        proxy.getEventManager().register(this, DisconnectEvent.class,
                disconnect -> skins.remove(disconnect.getPlayer().getUniqueId()));

        logger.info("Lobby bridge ready: lobbies {}, {} group(s), counts every {}s, status every {}s,"
                        + " client versions from {}, allowed commands {}.",
                config.lobbyServers(), config.groups().size(), config.updateIntervalSeconds(),
                config.statusIntervalSeconds(), viaVersion ? "ViaVersion" : "Velocity", config.allowedCommands());
    }

    /** When a player arrives on a lobby, send their real client version and fresh network data. */
    private void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer()
                .filter(connection -> isLobby(connection.getServerInfo().getName()))
                .ifPresent(connection -> {
                    send(connection, new BridgeMessage.ClientVersion(player.getUniqueId(), clientProtocol(player)));
                    // Remember the skin they arrive with, so only later changes are reported.
                    SkinsRestorerHook.Skin skin = SkinsRestorerHook.of(player);
                    if (skin != null) {
                        skins.put(player.getUniqueId(), skin);
                    }
                    send(connection, snapshot());
                    send(connection, new BridgeMessage.ServerStatus(statuses));
                });
    }

    /**
     * Handles every {@code lobby:bridge} message and never lets one through. Messages from players are
     * dropped: a player could otherwise forge them (fake client version, fake chat, commands).
     * Messages from a lobby's server connection are chat relays and command requests.
     */
    private void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        String origin = connection.getServerInfo().getName();
        if (!isLobby(origin)) {
            logger.warn("Ignored a bridge message from '{}', which is not in lobby-servers.", origin);
            return;
        }
        BridgeMessage message;
        try {
            message = BridgeCodec.decode(event.getData());
        } catch (BridgeFormatException e) {
            logger.warn("Ignored a bad bridge message from '{}': {}", origin, e.getMessage());
            return;
        }
        switch (message) {
            case BridgeMessage.ChatRelay chat -> relayChat(origin, chat);
            case BridgeMessage.CommandRequest request -> runCommand(connection, origin, request);
            case BridgeMessage.ConnectRequest request -> connectPlayer(connection, origin, request);
            default -> logger.debug("Ignored bridge message type {} from '{}'", message.typeId(), origin);
        }
    }

    /** Forwards a chat message to every other lobby that has players. */
    private void relayChat(String origin, BridgeMessage.ChatRelay chat) {
        // Trust the connection, not the message, for where it came from.
        BridgeMessage.ChatRelay checked = new BridgeMessage.ChatRelay(chat.messageId(), chat.channel(), origin,
                chat.senderId(), chat.senderName(), chat.prefix(), chat.suffix(), chat.primaryGroup(), chat.meta(),
                chat.message());
        byte[] data = BridgeCodec.encode(checked);
        for (String lobby : config.lobbyServers()) {
            if (lobby.equals(origin)) {
                continue;
            }
            proxy.getServer(lobby)
                    .filter(server -> !server.getPlayersConnected().isEmpty())
                    .ifPresent(server -> server.sendPluginMessage(CHANNEL, data));
        }
    }

    /** Runs an allowlisted console command and reports the result back to the lobby. */
    private void runCommand(ServerConnection connection, String origin, BridgeMessage.CommandRequest request) {
        String refusal = commandGate.refusalReason(request.command());
        if (refusal != null) {
            logger.warn("Refused command from lobby '{}' ({}): {} - {}", origin, request.reason(), request.command(), refusal);
            send(connection, new BridgeMessage.CommandResult(request.requestId(), false, refusal));
            return;
        }
        String command = CommandGate.normalize(request.command());
        logger.info("Lobby '{}' ran console command ({}): {}", origin, request.reason(), command);
        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command)
                .whenComplete((found, error) -> {
                    boolean ok = error == null && Boolean.TRUE.equals(found);
                    String detail = ok ? "" : error != null ? error.getMessage() : "command not found on the proxy";
                    if (!ok) {
                        logger.warn("Console command from lobby '{}' failed: {} - {}", origin, command, detail);
                    }
                    send(connection, new BridgeMessage.CommandResult(request.requestId(), ok, detail));
                });
    }

    /**
     * Sends a player to another server because their lobby asked. Only the lobby a player is really on
     * may move them, so one lobby cannot pull players off another server.
     */
    private void connectPlayer(ServerConnection connection, String origin, BridgeMessage.ConnectRequest request) {
        Player player = proxy.getPlayer(request.playerId()).orElse(null);
        if (player == null || player.getCurrentServer()
                .filter(current -> current.getServerInfo().getName().equals(origin)).isEmpty()) {
            logger.warn("Lobby {} asked to move a player who is not on it. Ignored.", origin);
            send(connection, result(request, Outcome.REFUSED, ""));
            return;
        }
        String target;
        if (request.group()) {
            List<String> servers = config.groups().get(request.target());
            if (servers == null) {
                logger.warn("Lobby {} asked for the group {}, which is not in config.toml.", origin, request.target());
                send(connection, result(request, Outcome.UNKNOWN, ""));
                return;
            }
            boolean allowFull = !config.joinFullPermission().isEmpty()
                    && player.hasPermission(config.joinFullPermission());
            ServerPicker.Choice choice = ServerPicker.pick(servers, serverStates(),
                    config.strategyOf(request.target()), allowFull);
            if (choice.server() == null) {
                send(connection, result(request, choice.outcome(), ""));
                return;
            }
            target = choice.server();
        } else {
            target = request.target();
        }
        RegisteredServer server = proxy.getServer(target).orElse(null);
        if (server == null) {
            logger.warn("Lobby {} asked for the server {}, which is not in velocity.toml.", origin, target);
            send(connection, result(request, Outcome.UNKNOWN, ""));
            return;
        }
        String chosen = target;
        player.createConnectionRequest(server).connect().whenComplete((outcome, error) -> {
            boolean ok = error == null && outcome.isSuccessful();
            if (!ok) {
                logger.warn("Could not send {} to {}: {}", player.getUsername(), chosen,
                        error != null ? error.getMessage() : outcome.getStatus());
            }
            send(connection, result(request, ok ? Outcome.CONNECTED : Outcome.REFUSED, chosen));
        });
    }

    private static BridgeMessage.ConnectResult result(BridgeMessage.ConnectRequest request, Outcome outcome,
                                                      String server) {
        return new BridgeMessage.ConnectResult(request.requestId(), request.playerId(), outcome, server);
    }

    /** What every server looks like right now, for {@link ServerPicker}. */
    private Map<String, ServerPicker.ServerState> serverStates() {
        Map<String, ServerPicker.ServerState> states = new LinkedHashMap<>();
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            BridgeMessage.ServerStatus.Status status = statuses.get(name);
            // Not pinged yet: count it as reachable, so a freshly started proxy still works.
            boolean reachable = status == null || status.online();
            int max = status == null ? 0 : status.maxPlayers();
            states.put(name, new ServerPicker.ServerState(name, server.getPlayersConnected().size(), max, reachable));
        }
        return states;
    }

    /** Tells the lobbies about skins that changed on the proxy, for example through /skin. */
    private void checkSkins() {
        for (Player player : proxy.getAllPlayers()) {
            player.getCurrentServer()
                    .filter(connection -> isLobby(connection.getServerInfo().getName()))
                    .ifPresent(connection -> SkinsRestorerHook.checkForChange(player, skins,
                            (changed, skin) -> send(connection, new BridgeMessage.SkinUpdate(
                                    changed.getUniqueId(), skin.value(), skin.signature()))));
        }
    }

    /** Sends counts and status to every lobby that has players (messages travel through a player). */
    private void pushSnapshot() {
        byte[] snapshot = BridgeCodec.encode(snapshot());
        byte[] status = BridgeCodec.encode(new BridgeMessage.ServerStatus(statuses));
        for (String name : config.lobbyServers()) {
            proxy.getServer(name)
                    .filter(server -> !server.getPlayersConnected().isEmpty())
                    .ifPresent(server -> {
                        server.sendPluginMessage(CHANNEL, snapshot);
                        server.sendPluginMessage(CHANNEL, status);
                    });
        }
    }

    /** Pings every backend server and remembers whether it answered. */
    private void pingServers() {
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            server.ping()
                    .orTimeout(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((ping, error) -> statuses.put(name, error == null
                            ? new BridgeMessage.ServerStatus.Status(true, maxPlayers(ping))
                            : new BridgeMessage.ServerStatus.Status(false, 0)));
        }
    }

    private static int maxPlayers(ServerPing ping) {
        return ping.getPlayers().map(ServerPing.Players::getMax).orElse(0);
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
