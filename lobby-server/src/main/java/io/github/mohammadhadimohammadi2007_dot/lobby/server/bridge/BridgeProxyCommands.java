package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ProxyCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs commands on the proxy as a player through the bridge ({@link BridgeMessage.PlayerCommandRequest}), and
 * tells the player when the proxy did not run it.
 */
public final class BridgeProxyCommands implements ProxyCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeProxyCommands.class);

    private final BridgeService bridge;
    private final LobbyText text;
    private final AtomicBoolean warnedOldBridge = new AtomicBoolean();

    public BridgeProxyCommands(BridgeService bridge, LobbyText text) {
        this.bridge = bridge;
        this.text = text;
    }

    @Override
    public CompletableFuture<Boolean> run(Player player, String command, String source) {
        if (!bridge.bridgeRunsPlayerCommands()) {
            if (warnedOldBridge.compareAndSet(false, true)) {
                LOGGER.warn("'/{}' of {} has to run on the proxy, but the lobby bridge there is older than {} and"
                        + " cannot do that. Update lobby-bridge.jar on the proxy.", command, source,
                        BridgeService.PLAYER_COMMANDS_SINCE);
            }
            tell(player, MessageKey.PROXY_COMMAND_FAILED, command);
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> ran = new CompletableFuture<>();
        bridge.requestPlayerCommand(player, command).whenComplete((result, error) -> {
            if (error != null) {
                LOGGER.warn("The proxy did not answer when {} asked to run '/{}' as {}: {}", source, command,
                        player.getUsername(), error.getMessage());
                tell(player, MessageKey.PROXY_COMMAND_FAILED, command);
                ran.complete(false);
                return;
            }
            switch (result.outcome()) {
                case EXECUTED -> {
                }
                case NOT_FOUND -> {
                    LOGGER.warn("The proxy has no command '/{}' (from {}), or {} may not use it there", command,
                            source, player.getUsername());
                    tell(player, MessageKey.PROXY_COMMAND_UNKNOWN, command);
                }
                case FAILED -> tell(player, MessageKey.PROXY_COMMAND_FAILED, command);
            }
            ran.complete(result.outcome() == BridgeMessage.PlayerCommandResult.Outcome.EXECUTED);
        });
        return ran;
    }

    private void tell(Player player, MessageKey key, String command) {
        if (player.isOnline()) {
            player.sendMessage(text.message(key, player, Messages.text("command", command)));
        }
    }
}
