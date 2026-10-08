package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;

/** Refuses new players when the lobby already has {@code server.max-players} players. */
public final class PlayerLimitListener {

    private final ConfigManager configManager;
    private final LobbyText text;

    public PlayerLimitListener(ConfigManager configManager, LobbyText text) {
        this.configManager = configManager;
        this.text = text;
    }

    /** Registers the listener on {@code node}. */
    public void register(EventNode<Event> node) {
        node.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            ConfigSnapshot snapshot = configManager.current();
            int online = MinecraftServer.getConnectionManager().getOnlinePlayerCount();
            if (online >= snapshot.config().server().maxPlayers()) {
                event.getConnection().kick(text.message(MessageKey.KICK_SERVER_FULL));
            }
        });
    }
}
