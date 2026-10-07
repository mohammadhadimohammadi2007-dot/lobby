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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives messages from the lobby-bridge proxy plugin and keeps {@link ClientCapabilities} and
 * {@link NetworkState} up to date.
 *
 * <p>Works without a bridge: capabilities default to the server's own (modern) protocol and all
 * counts stay 0. Messages are only accepted when the bridge is enabled and the server is behind a
 * proxy; in standalone mode a player could forge them.
 */
public final class BridgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeService.class);
    /** Time without a snapshot after which the bridge is reported as silent. */
    private static final long SILENT_AFTER_MILLIS = 30_000;

    private final boolean listening;
    private final NetworkState networkState = new NetworkState();
    private final Map<UUID, ClientCapabilities> capabilities = new ConcurrentHashMap<>();

    /** @param listening true if bridge messages should be accepted (bridge enabled and behind a proxy) */
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
                handle(event.getPlayer(), event.getMessage());
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> capabilities.remove(event.getPlayer().getUuid()));
    }

    private void handle(Player carrier, byte[] data) {
        BridgeMessage message;
        try {
            message = BridgeCodec.decode(data);
        } catch (BridgeFormatException e) {
            LOGGER.warn("Ignored a bad bridge message (via {}): {}", carrier.getUsername(), e.getMessage());
            return;
        }
        switch (message) {
            case BridgeMessage.ClientVersion version ->
                    capabilities.put(version.playerId(), new ClientCapabilities(version.protocolVersion(), true));
            case BridgeMessage.NetworkSnapshot snapshot -> networkState.update(snapshot);
        }
    }

    /** What a player's client supports. Defaults to modern when the bridge has not reported anything. */
    public ClientCapabilities capabilities(Player player) {
        ClientCapabilities reported = capabilities.get(player.getUuid());
        return reported != null ? reported : new ClientCapabilities(MinecraftServer.PROTOCOL_VERSION, false);
    }

    /** Latest player counts and groups from the proxy. */
    public NetworkState networkState() {
        return networkState;
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
