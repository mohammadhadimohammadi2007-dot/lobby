package io.github.mohammadhadimohammadi2007_dot.lobby.server.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code /stop}: saves everything and shuts the server down. Console and {@code lobby.command.stop}. */
public final class StopCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(StopCommand.class);

    public StopCommand(LobbyText text, PermissionService permissions) {
        super("stop");
        setCondition(CommandSupport.requireAny(text, permissions, Permissions.COMMAND_STOP));
        setDefaultExecutor((sender, context) -> {
            LOGGER.info("Stopping the server ({})...", CommandSupport.name(sender));
            MinecraftServer.stopCleanly();
        });
    }
}
