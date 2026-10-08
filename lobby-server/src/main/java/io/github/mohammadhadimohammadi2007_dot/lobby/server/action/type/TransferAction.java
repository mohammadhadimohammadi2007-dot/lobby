package io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.Action;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.TransferPacket;

/**
 * {@code transfer: <host> [port]} moves the player to another server address with Minecraft's transfer
 * packet, without a proxy. Only clients 1.20.5 and newer support it (others get a message), and the target
 * server must accept transfers ({@code accepts-transfers=true} on vanilla and Paper).
 */
public record TransferAction(String host, int port) implements Action {

    public static final int DEFAULT_PORT = 25565;

    @Override
    public Step run(ActionContext context) {
        Player player = context.player();
        if (context.services().bridge().capabilities(player).protocolVersion() < ProtocolVersions.V1_20_5) {
            player.sendMessage(context.services().text().message(MessageKey.TRANSFER_NOT_SUPPORTED, player,
                    Messages.text("host", port == DEFAULT_PORT ? host : host + ":" + port)));
            return Step.STOP;
        }
        player.sendPacket(new TransferPacket(host, port));
        return Step.STOP;
    }

    @Override
    public String describe() {
        return "transfer: " + host + " " + port;
    }
}
