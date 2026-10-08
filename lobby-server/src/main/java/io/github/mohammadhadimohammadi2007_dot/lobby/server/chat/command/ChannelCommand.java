package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;

import java.util.Locale;
import java.util.stream.Collectors;

/** {@code /ch <channel>} (alias {@code /channel}): choose the chat channel you write in. */
public final class ChannelCommand extends Command {

    public ChannelCommand(ChatServices services) {
        super("ch", "channel");
        setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_CHANNEL));
        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                player.sendMessage(services.text().message(MessageKey.CH_USAGE, player,
                        Messages.text("channels", usableChannels(services, player))));
            }
        });
        ArgumentWord channelArgument = ArgumentType.Word("channel");
        channelArgument.setSuggestionCallback((sender, context, suggestion) -> {
            if (sender instanceof Player player) {
                for (ChatConfig.Channel channel : services.chat().channels().values()) {
                    if (canUse(services, player, channel)) {
                        suggestion.addEntry(new SuggestionEntry(channel.name()));
                    }
                }
            }
        });
        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(services.text().message(MessageKey.PLAYERS_ONLY, sender));
                return;
            }
            String name = context.get(channelArgument).toLowerCase(Locale.ROOT);
            ChatConfig.Channel channel = services.chat().channels().get(name);
            if (channel == null) {
                player.sendMessage(services.text().message(MessageKey.CHANNEL_UNKNOWN, player,
                        Messages.text("channels", usableChannels(services, player))));
                return;
            }
            if (!canUse(services, player, channel)) {
                player.sendMessage(services.text().message(MessageKey.CHANNEL_NO_PERMISSION, player,
                        Messages.text("channel", channel.name())));
                return;
            }
            services.settings().update(player.getUuid(), settings -> settings.withChannel(channel.name()));
            player.sendMessage(services.text().message(MessageKey.CHANNEL_SWITCHED, player, Messages.text("channel", channel.name())));
        }, channelArgument);
    }

    private static boolean canUse(ChatServices services, Player player, ChatConfig.Channel channel) {
        return services.has(player, channel.permission()) && services.has(player, channel.sendPermission());
    }

    private static String usableChannels(ChatServices services, Player player) {
        return services.chat().channels().values().stream()
                .filter(channel -> canUse(services, player, channel))
                .map(channel -> channel.prefix().isEmpty() ? channel.name() : channel.name() + " (" + channel.prefix() + ")")
                .collect(Collectors.joining(", "));
    }
}
