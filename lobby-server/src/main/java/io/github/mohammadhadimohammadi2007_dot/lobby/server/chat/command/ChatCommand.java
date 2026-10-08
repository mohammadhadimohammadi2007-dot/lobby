package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.PlayerChatSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.entity.Player;

import java.util.function.Function;

/**
 * {@code /chat}: player settings ({@code toggle}, {@code persian}, {@code mentions}) and staff tools
 * ({@code clear}, {@code lock}, {@code delete <id>}, {@code spy}).
 */
public final class ChatCommand extends Command {

    private final ChatServices services;

    public ChatCommand(ChatService chat) {
        super("chat");
        this.services = chat.services();
        setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_CHAT));
        setDefaultExecutor((sender, context) -> usage(sender));

        addSubcommand(playerSub("toggle", player -> {
            if (!services.chat().chatToggleEnabled()) {
                return MessageKey.FEATURE_DISABLED;
            }
            boolean visible = !services.settings().get(player.getUuid()).chatVisible();
            services.settings().update(player.getUuid(), settings -> settings.withChatVisible(visible));
            return visible ? MessageKey.CHAT_SHOWN : MessageKey.CHAT_HIDDEN;
        }));
        addSubcommand(playerSub("persian", player -> {
            PlayerChatSettings current = services.settings().get(player.getUuid());
            boolean now = current.persian() != null ? current.persian() : services.chat().persian().defaultOn();
            services.settings().update(player.getUuid(), settings -> settings.withPersian(!now));
            return now ? MessageKey.PERSIAN_OFF : MessageKey.PERSIAN_ON;
        }));
        addSubcommand(playerSub("mentions", player -> {
            boolean enabled = !services.settings().get(player.getUuid()).mentions();
            services.settings().update(player.getUuid(), settings -> settings.withMentions(enabled));
            return enabled ? MessageKey.MENTIONS_ON : MessageKey.MENTIONS_OFF;
        }));

        Command clear = new Command("clear");
        clear.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_CLEAR));
        clear.setDefaultExecutor((sender, context) ->
                chat.clearChat(services.text().message(MessageKey.CHAT_CLEARED, Messages.text("staff", name(sender)))));
        addSubcommand(clear);

        Command lock = new Command("lock");
        lock.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_LOCK));
        lock.setDefaultExecutor((sender, context) -> {
            boolean locked = !services.moderation().locked();
            services.moderation().locked(locked);
            services.tellStaff("", services.text().message(locked ? MessageKey.CHAT_LOCKED_ON : MessageKey.CHAT_LOCKED_OFF,
                    Messages.text("staff", name(sender))));
        });
        addSubcommand(lock);

        Command delete = new Command("delete");
        delete.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_DELETE));
        ArgumentWord id = ArgumentType.Word("id");
        delete.addSyntax((sender, context) -> {
            String messageId = context.get(id);
            chat.deleteMessage(messageId).thenAccept(found -> sender.sendMessage(found
                    ? services.text().message(MessageKey.MESSAGE_DELETED, sender)
                    : services.text().message(MessageKey.MESSAGE_NOT_FOUND, sender, Messages.text("id", messageId))));
        }, id);
        addSubcommand(delete);

        Command spy = new Command("spy");
        spy.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_SPY));
        spy.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                boolean on = services.moderation().toggleSpy(player.getUuid());
                player.sendMessage(services.text().message(on ? MessageKey.SPY_ON : MessageKey.SPY_OFF, player));
            }
        });
        addSubcommand(spy);
    }

    /** A sub command that changes the player's own settings and replies with the returned message. */
    private Command playerSub(String name, Function<Player, MessageKey> action) {
        Command command = new Command(name);
        command.setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player player) {
                player.sendMessage(services.text().message(action.apply(player), player));
            } else {
                sender.sendMessage(services.text().message(MessageKey.PLAYERS_ONLY, sender));
            }
        });
        return command;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(services.text().message(MessageKey.CHAT_USAGE, sender));
        boolean staff = services.permissions().hasPermission(sender, ChatPermissions.COMMAND_CLEAR)
                || services.permissions().hasPermission(sender, ChatPermissions.COMMAND_LOCK)
                || services.permissions().hasPermission(sender, ChatPermissions.COMMAND_DELETE);
        if (staff) {
            sender.sendMessage(services.text().message(MessageKey.CHAT_STAFF_USAGE, sender));
        }
    }

    private static String name(CommandSender sender) {
        return sender instanceof Player player ? player.getUsername() : "Console";
    }
}
