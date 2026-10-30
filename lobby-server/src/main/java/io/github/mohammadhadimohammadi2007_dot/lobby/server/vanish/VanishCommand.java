package io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;

/**
 * {@code /vanish} switches vanish for yourself (permission {@code lobby.command.vanish}); {@code /vanish <player>}
 * for someone else ({@code lobby.command.vanish.others}, also from the console).
 */
public final class VanishCommand extends Command {

    private final VanishService vanish;
    private final LobbyText text;
    private final PermissionService permissions;

    public VanishCommand(VanishService vanish, LobbyText text, PermissionService permissions) {
        super("vanish", "v");
        this.vanish = vanish;
        this.text = text;
        this.permissions = permissions;
        setCondition((sender, commandString) -> {
            boolean allowed = permissions.hasPermission(sender, Permissions.COMMAND_VANISH)
                    || permissions.hasPermission(sender, Permissions.COMMAND_VANISH_OTHERS);
            if (!allowed && commandString != null) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
            }
            return allowed;
        });
        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text.message(MessageKey.NAME_A_PLAYER, sender));
            } else if (!permissions.hasPermission(player, Permissions.COMMAND_VANISH)) {
                player.sendMessage(text.message(MessageKey.NO_PERMISSION, player));
            } else {
                toggle(player, player);
            }
        });
        ArgumentWord target = ArgumentType.Word("player");
        // Only those who may see vanished players get vanished names suggested.
        target.setSuggestionCallback((sender, context, suggestion) -> {
            boolean seesVanished = !(sender instanceof Player player) || vanish.canSeeVanished(player);
            for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (seesVanished || !Vanished.is(online)) {
                    suggestion.addEntry(new SuggestionEntry(online.getUsername()));
                }
            }
        });
        addSyntax((sender, context) -> {
            if (!permissions.hasPermission(sender, Permissions.COMMAND_VANISH_OTHERS)) {
                sender.sendMessage(text.message(MessageKey.NO_PERMISSION, sender));
                return;
            }
            String name = context.get(target);
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name);
            if (player == null) {
                sender.sendMessage(text.message(MessageKey.PLAYER_NOT_ONLINE, sender, Messages.text("player", name)));
                return;
            }
            toggle(sender, player);
        }, target);
    }

    private void toggle(CommandSender sender, Player player) {
        boolean vanishing = !Vanished.is(player);
        switch (vanish.set(player, vanishing)) {
            case DONE -> {
                if (sender != player) {
                    sender.sendMessage(text.message(vanishing ? MessageKey.VANISH_OTHER_ON : MessageKey.VANISH_OTHER_OFF,
                            sender, Messages.text("player", player.getUsername())));
                }
            }
            case IN_PVP_AREA -> sender.sendMessage(text.message(MessageKey.VANISH_IN_PVP, sender,
                    Messages.text("player", player.getUsername())));
            case UNCHANGED, CANCELLED -> { }
        }
    }
}
