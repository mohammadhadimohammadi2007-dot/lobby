package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeCodec;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeFormatException;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeProtocol;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Talks to the lobby-bridge proxy plugin: keeps {@link ClientCapabilities} and {@link NetworkState} up to
 * date, receives chat relayed from other lobbies, and sends chat and command requests to the proxy.
 *
 * <p>Works without a bridge: capabilities default to the server's own (modern) protocol, all counts stay
 * 0, {@link #sendChat} returns false and {@link #requestCommand} fails right away, so callers fall back to
 * local behavior. Messages are only accepted when the bridge is enabled and the server is behind a proxy;
 * in standalone mode a player could forge them.
 */
public final class BridgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeService.class);
    /** Time without a snapshot after which the bridge is reported as silent. */
    private static final long SILENT_AFTER_MILLIS = 30_000;
    /** How long to wait for the proxy to answer a command request. */
    private static final long COMMAND_TIMEOUT_SECONDS = 10;
    /** How long to wait for the proxy to answer a connect request. */
    private static final long CONNECT_TIMEOUT_SECONDS = 10;

    private final boolean listening;
    private final NetworkState networkState = new NetworkState();
    private final Map<UUID, ClientCapabilities> capabilities = new ConcurrentHashMap<>();
    private final List<Consumer<BridgeMessage.ChatRelay>> chatListeners = new CopyOnWriteArrayList<>();
    private final Map<String, CompletableFuture<BridgeMessage.CommandResult>> pendingCommands = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<BridgeMessage.ConnectResult>> pendingConnects = new ConcurrentHashMap<>();
    private final List<Consumer<BridgeMessage.SkinUpdate>> skinListeners = new CopyOnWriteArrayList<>();

    /** @param listening true if the bridge is enabled and the server is behind a proxy */
    public BridgeService(boolean listening) {
        this.listening = listening;
    }

    /** Registers the listeners on {@code node}. Does nothing if the bridge is off. */
    public void register(EventNode<PlayerEvent> node) {
        if (!listening) {
            return;
        }
        node.addListener(PlayerPluginMessageEvent.class, event -> {
            if (BridgeProtocol.CHANNEL.equals(event.getIdentifier())) {
                receive(event.getMessage(), event.getPlayer().getUsername());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> capabilities.remove(event.getPlayer().getUuid()));
    }

    /** True if messages can reach the proxy right now: the bridge is on and a player is online to carry them. */
    public boolean available() {
        return listening && !MinecraftServer.getConnectionManager().getOnlinePlayers().isEmpty();
    }

    /** Runs {@code listener} for every chat message relayed from another lobby. Called on a network thread. */
    public void onChatRelay(Consumer<BridgeMessage.ChatRelay> listener) {
        chatListeners.add(listener);
    }

    /**
     * Sends a chat message to the other lobbies, through {@code carrier}'s connection.
     *
     * @return false if the bridge is not available; the caller then keeps the message local
     */
    public boolean sendChat(Player carrier, BridgeMessage.ChatRelay message) {
        if (!listening) {
            return false;
        }
        return send(carrier, message);
    }

    /**
     * Asks the proxy to run a console command (for example a LiteBans mute). The proxy only runs commands
     * from its {@code allowed-commands} list.
     *
     * @return the proxy's answer; completes exceptionally if the bridge is not available or does not answer
     */
    public CompletableFuture<BridgeMessage.CommandResult> requestCommand(String command, String reason) {
        if (!listening) {
            return CompletableFuture.failedFuture(new IllegalStateException("the bridge is turned off"));
        }
        Optional<Player> carrier = anyOnlinePlayer();
        if (carrier.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("no player online to carry the request"));
        }
        String requestId = shortId();
        CompletableFuture<BridgeMessage.CommandResult> result = new CompletableFuture<>();
        pendingCommands.put(requestId, result);
        if (!send(carrier.get(), new BridgeMessage.CommandRequest(requestId, command, reason))) {
            pendingCommands.remove(requestId);
            return CompletableFuture.failedFuture(new IllegalStateException("could not send the request"));
        }
        return result.orTimeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> pendingCommands.remove(requestId));
    }

    /**
     * Asks the proxy to send {@code player} to another server. The lobby cannot move players itself.
     *
     * @param target a server name, or a group name if {@code group} is true
     * @return what happened; completes exceptionally if the bridge is off or does not answer
     */
    public CompletableFuture<BridgeMessage.ConnectResult> requestConnect(Player player, String target, boolean group) {
        if (!listening) {
            return CompletableFuture.failedFuture(new IllegalStateException("the bridge is turned off"));
        }
        String requestId = shortId();
        CompletableFuture<BridgeMessage.ConnectResult> result = new CompletableFuture<>();
        pendingConnects.put(requestId, result);
        if (!send(player, new BridgeMessage.ConnectRequest(requestId, player.getUuid(), target, group))) {
            pendingConnects.remove(requestId);
            return CompletableFuture.failedFuture(new IllegalStateException("could not send the request"));
        }
        return result.orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> pendingConnects.remove(requestId));
    }

    /** Runs {@code listener} when the proxy reports a changed skin. Called on a network thread. */
    public void onSkinUpdate(Consumer<BridgeMessage.SkinUpdate> listener) {
        skinListeners.add(listener);
    }

    /**
     * Decodes and applies one message. Package-private so tests can feed messages without a proxy.
     *
     * @param via name of the player whose connection carried it, for logs
     */
    void receive(byte[] data, String via) {
        BridgeMessage message;
        try {
            message = BridgeCodec.decode(data);
        } catch (BridgeFormatException e) {
            LOGGER.warn("Ignored a bad bridge message (via {}): {}", via, e.getMessage());
            return;
        }
        switch (message) {
            case BridgeMessage.ClientVersion version ->
                    capabilities.put(version.playerId(), new ClientCapabilities(version.protocolVersion(), true));
            case BridgeMessage.NetworkSnapshot snapshot -> networkState.update(snapshot);
            case BridgeMessage.ServerStatus status -> networkState.update(status);
            case BridgeMessage.ChatRelay chat -> chatListeners.forEach(listener -> listener.accept(chat));
            case BridgeMessage.CommandResult result -> completeCommand(result);
            case BridgeMessage.ConnectResult result -> completeConnect(result);
            case BridgeMessage.SkinUpdate skin -> skinListeners.forEach(listener -> listener.accept(skin));
            // Only the proxy receives requests; a newer proxy may also send types we do not know yet.
            case BridgeMessage.CommandRequest ignored -> LOGGER.debug("Ignored a command request sent to the lobby");
            case BridgeMessage.ConnectRequest ignored -> LOGGER.debug("Ignored a connect request sent to the lobby");
            case BridgeMessage.Unknown unknown -> LOGGER.debug("Ignored unknown bridge message type {}", unknown.typeId());
        }
    }

    private void completeCommand(BridgeMessage.CommandResult result) {
        CompletableFuture<BridgeMessage.CommandResult> pending = pendingCommands.remove(result.requestId());
        if (pending != null) {
            pending.complete(result);
        }
    }

    private void completeConnect(BridgeMessage.ConnectResult result) {
        CompletableFuture<BridgeMessage.ConnectResult> pending = pendingConnects.remove(result.requestId());
        if (pending != null) {
            pending.complete(result);
        }
    }

    private boolean send(Player carrier, BridgeMessage message) {
        try {
            carrier.sendPluginMessage(BridgeProtocol.CHANNEL, BridgeCodec.encode(message));
            return true;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Could not send bridge message: {}", e.getMessage());
            return false;
        }
    }

    private static Optional<Player> anyOnlinePlayer() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers().stream().findAny();
    }

    /** A short random id such as {@code 4f9a2c}, for messages and requests. */
    public static String shortId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong(0x100000000L, 0x1000000000L));
    }

    /** What a player's client supports. Defaults to modern when the bridge has not reported anything. */
    public ClientCapabilities capabilities(Player player) {
        ClientCapabilities reported = capabilities.get(player.getUuid());
        return reported != null ? reported : new ClientCapabilities(MinecraftServer.PROTOCOL_VERSION, false);
    }

    /** Latest player counts, groups and server statuses from the proxy. */
    public NetworkState networkState() {
        return networkState;
    }

    /** True if the bridge is turned on (behind a proxy with bridge.enabled). */
    public boolean enabled() {
        return listening;
    }

    /** Bridge state in words, for {@code /lobby info}. */
    public String status() {
        if (!listening) {
            return "disabled";
        }
        return networkState.lastUpdateMillis()
                .map(last -> {
                    long ago = System.currentTimeMillis() - last;
                    String when = "last update " + (ago / 1000) + "s ago";
                    return ago > SILENT_AFTER_MILLIS ? "silent (" + when + ")" : "connected (" + when + ")";
                })
                .orElse("waiting for the proxy");
    }
}
