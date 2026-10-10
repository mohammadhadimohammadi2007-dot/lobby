package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Connector;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Sends players to other servers by asking the proxy through the bridge, and tells them what happened:
 * the server was full, offline, or does not exist.
 */
public final class BridgeConnector implements Connector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeConnector.class);

    private final BridgeService bridge;
    private final LobbyText text;

    public BridgeConnector(BridgeService bridge, LobbyText text) {
        this.bridge = bridge;
        this.text = text;
    }

    @Override
    public CompletableFuture<Boolean> connect(Player player, String server) {
        return request(player, server, false);
    }

    @Override
    public CompletableFuture<Boolean> connectGroup(Player player, String group) {
        return request(player, group, true);
    }

    private CompletableFuture<Boolean> request(Player player, String target, boolean group) {
        player.sendMessage(text.message(MessageKey.CONNECTING, player, Messages.text("server", target)));
        CompletableFuture<Boolean> sent = new CompletableFuture<>();
        bridge.requestConnect(player, target, group).whenComplete((result, error) -> {
            if (error != null) {
                LOGGER.warn("The proxy did not answer the request to send {} to '{}': {}",
                        player.getUsername(), target, error.getMessage());
                tell(player, MessageKey.CONNECT_FAILED, target);
                sent.complete(false);
                return;
            }
            sent.complete(result.outcome() == BridgeMessage.ConnectResult.Outcome.CONNECTED);
            switch (result.outcome()) {
                // The player is already on their way; nothing more to say.
                case CONNECTED -> {
                }
                case FULL -> tell(player, MessageKey.CONNECT_FULL, target);
                case OFFLINE -> tell(player, MessageKey.CONNECT_OFFLINE, target);
                case UNKNOWN -> {
                    LOGGER.warn("The proxy does not know the {} '{}'. Check its name in config.toml and velocity.toml.",
                            group ? "group" : "server", target);
                    tell(player, MessageKey.CONNECT_UNKNOWN, target);
                }
                case REFUSED -> tell(player, MessageKey.CONNECT_FAILED, target);
            }
        });
        return sent;
    }

    private void tell(Player player, MessageKey key, String target) {
        if (player.isOnline()) {
            player.sendMessage(text.message(key, player, Messages.text("server", target)));
        }
    }
}
