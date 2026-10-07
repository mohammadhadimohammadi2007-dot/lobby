package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.ping.Status;

/** Fills in the server list entry (MOTD and player count) from config.yml. */
public final class ServerListListener {

    private final ConfigManager configManager;

    public ServerListListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Starts answering server list pings. */
    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent.class, this::onPing);
    }

    private void onPing(ServerListPingEvent event) {
        LobbyConfig.Server server = configManager.current().config().server();
        Status.PlayerInfo players = event.getStatus().playerInfo();
        event.setStatus(Status.builder(event.getStatus())
                .description(Messages.parse(server.motd()))
                .playerInfo(Status.PlayerInfo.builder(players != null ? players : Status.PlayerInfo.onlineCount())
                        .maxPlayers(server.maxPlayers())
                        .build())
                .build());
    }
}
