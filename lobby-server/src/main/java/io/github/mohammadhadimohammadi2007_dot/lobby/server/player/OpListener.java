package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;

/**
 * Checks operators as they join (before anything uses their permissions), tells one who must confirm
 * with a code, and forgets them when they leave.
 */
public final class OpListener {

    private final OpList ops;
    private final LobbyText text;

    public OpListener(OpList ops, LobbyText text) {
        this.ops = ops;
        this.text = text;
    }

    public void register(EventNode<PlayerEvent> node) {
        // During configuration: the player has their UUID and address, and nothing has checked permissions yet.
        node.addListener(AsyncPlayerConfigurationEvent.class, event -> ops.joined(event.getPlayer()));
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn() && ops.hasPendingCode(event.getPlayer().getUuid())) {
                event.getPlayer().sendMessage(text.message(MessageKey.OP_CONFIRM_NEEDED, event.getPlayer()));
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> ops.left(event.getPlayer().getUuid()));
    }
}
