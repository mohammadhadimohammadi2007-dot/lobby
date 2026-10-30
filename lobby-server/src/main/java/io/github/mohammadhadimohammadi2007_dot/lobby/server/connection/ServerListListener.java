package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish.Vanished;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.ping.Status;
import net.minestom.server.utils.identity.NamedAndIdentified;

import java.util.List;

/** Fills in the server list entry (MOTD and player count) from config.yml. The MOTD may use global placeholders. */
public final class ServerListListener {

    private final ConfigManager configManager;
    private final LobbyText text;

    public ServerListListener(ConfigManager configManager, LobbyText text) {
        this.configManager = configManager;
        this.text = text;
    }

    /** Starts answering server list pings. */
    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent.class, this::onPing);
    }

    private void onPing(ServerListPingEvent event) {
        LobbyConfig.Server server = configManager.current().config().server();
        Status.PlayerInfo players = event.getStatus().playerInfo();
        if (players == null) {
            players = Status.PlayerInfo.onlineCount();
        }
        // Vanished staff are neither counted nor named in the list's player sample.
        List<NamedAndIdentified> sample = players.sample().stream()
                .filter(entry -> !Vanished.is(entry.getUuid())).toList();
        int online = Vanished.visibleCount(MinecraftServer.getConnectionManager().getOnlinePlayers());
        event.setStatus(Status.builder(event.getStatus())
                .description(text.render(server.motd(), null))
                .playerInfo(new Status.PlayerInfo(online, server.maxPlayers(), sample))
                .build());
    }
}
