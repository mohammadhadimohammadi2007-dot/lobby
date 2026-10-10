package io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Connector;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.io.UncheckedIOException;

/**
 * Sends players to other servers on BungeeCord and Waterfall, which understand the old
 * {@code BungeeCord} plugin message channel. No plugin is needed on the proxy for this.
 *
 * <p>BungeeCord has no server groups and tells the lobby nothing about its servers, so
 * {@code connect_group} cannot work here: it tells the player it is not available. Use Velocity with the
 * bridge plugin for selectors that pick the best server of a group.
 */
public final class BungeeConnector implements Connector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BungeeConnector.class);
    /** The channel BungeeCord has listened on since 1.13. */
    private static final String CHANNEL = "bungeecord:main";
    private static final String CONNECT = "Connect";

    private final LobbyText text;
    private boolean warnedAboutGroups;

    public BungeeConnector(LobbyText text) {
        this.text = text;
    }

    @Override
    public CompletableFuture<Boolean> connect(Player player, String server) {
        player.sendMessage(text.message(MessageKey.CONNECTING, player, Messages.text("server", server)));
        player.sendPluginMessage(CHANNEL, message(server));
        // BungeeCord never answers, so a failure cannot be seen here.
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> connectGroup(Player player, String group) {
        if (!warnedAboutGroups) {
            warnedAboutGroups = true;
            LOGGER.warn("A 'connect_group: {}' action needs Velocity with the lobby-bridge plugin, because"
                    + " BungeeCord does not tell the lobby about its servers. Use 'connect: <server>' instead.", group);
        }
        player.sendMessage(text.message(MessageKey.CONNECT_NOT_AVAILABLE, player));
        return CompletableFuture.completedFuture(false);
    }

    /** The {@code Connect} message: the word, then the server name, as BungeeCord expects. */
    private static byte[] message(String server) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(CONNECT);
            out.writeUTF(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
