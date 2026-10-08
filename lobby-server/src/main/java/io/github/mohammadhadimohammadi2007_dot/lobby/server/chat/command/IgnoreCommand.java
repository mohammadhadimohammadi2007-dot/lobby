package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;

import java.util.UUID;
import java.util.stream.Collectors;

/** {@code /ignore <player>} toggles hiding a player's messages; {@code /ignore list} shows who is ignored. */
public final class IgnoreCommand extends Command {

    public IgnoreCommand(ChatServices services) {
        super("ignore");
        setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_IGNORE));
        setDefaultExecutor((sender, context) -> sender.sendMessage(services.text().message(MessageKey.IGNORE_USAGE, sender)));
        ArgumentWord target = ArgumentType.Word("player");
        target.setSuggestionCallback((sender, context, suggestion) -> {
            for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                suggestion.addEntry(new SuggestionEntry(online.getUsername()));
            }
        });
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(services.text().message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            if (!services.chat().ignoreEnabled()) {
                player.sendMessage(services.text().message(MessageKey.FEATURE_DISABLED, player));
                return;
            }
            String name = context.get(target);
            if (name.equalsIgnoreCase("list")) {
                list(services, player);
                return;
            }
            Player other = MinecraftServer.getConnectionManager().findOnlinePlayer(name);
            if (other == null) {
                player.sendMessage(services.text().message(MessageKey.PLAYER_NOT_FOUND, player, Messages.text("name", name)));
                return;
            }
            if (other.getUuid().equals(player.getUuid())) {
                player.sendMessage(services.text().message(MessageKey.IGNORE_SELF, player));
                return;
            }
            PlayerChatSettings current = services.settings().get(player.getUuid());
            boolean ignore = !current.ignored().contains(other.getUuid());
            if (ignore && current.ignored().size() >= PlayerChatSettings.MAX_IGNORED) {
                return; // Limit reached; keeps one player's settings from growing without end.
            }
            services.settings().update(player.getUuid(), settings -> settings.withIgnored(other.getUuid(), ignore));
            player.sendMessage(services.text().message(ignore ? MessageKey.IGNORE_ADDED : MessageKey.IGNORE_REMOVED, player,
                    Messages.text("player", other.getUsername())));
        }, target);
    }

    private static void list(ChatServices services, Player player) {
        var ignored = services.settings().get(player.getUuid()).ignored();
        if (ignored.isEmpty()) {
            player.sendMessage(services.text().message(MessageKey.IGNORE_EMPTY, player));
            return;
        }
        String names = ignored.stream().map(IgnoreCommand::nameOf).collect(Collectors.joining(", "));
        player.sendMessage(services.text().message(MessageKey.IGNORE_LIST, player, Messages.text("players", names)));
    }

    /** The player's name if online, otherwise the start of their id. */
    private static String nameOf(UUID id) {
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
        return online != null ? online.getUsername() : id.toString().substring(0, 8);
    }
}
