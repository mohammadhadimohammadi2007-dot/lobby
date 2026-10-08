package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.command;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter.ChatFilter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /msg <player> <message>} (aliases tell, w) and {@code /r <message>}. Only registered when
 * {@code private-messages-enabled} is true in chat.yml; keep it off if a proxy plugin handles private messages.
 * Mutes, ignores and the chat filter apply.
 */
public final class PrivateMessageCommands {

    private final ChatServices services;
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public PrivateMessageCommands(ChatServices services) {
        this.services = services;
    }

    /** The two commands. */
    public List<Command> commands() {
        return List.of(msgCommand(), replyCommand());
    }

    private Command msgCommand() {
        Command command = new Command("msg", "tell", "w");
        command.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_MSG));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(services.text().message(MessageKey.MSG_USAGE, sender)));
        ArgumentWord target = ArgumentType.Word("player");
        target.setSuggestionCallback((sender, context, suggestion) -> {
            for (Player online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                suggestion.addEntry(new SuggestionEntry(online.getUsername()));
            }
        });
        ArgumentStringArray text = ArgumentType.StringArray("message");
        command.addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                Player to = MinecraftServer.getConnectionManager().findOnlinePlayer(context.get(target));
                if (to == null) {
                    player.sendMessage(services.text().message(MessageKey.PLAYER_NOT_FOUND, player,
                            Messages.text("name", context.get(target))));
                    return;
                }
                send(player, to, String.join(" ", context.get(text)));
            }
        }, target, text);
        return command;
    }

    private Command replyCommand() {
        Command command = new Command("r", "reply");
        command.setCondition(ChatCommandSupport.require(services, ChatPermissions.COMMAND_MSG));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(services.text().message(MessageKey.MSG_USAGE, sender)));
        ArgumentStringArray text = ArgumentType.StringArray("message");
        command.addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                UUID partner = lastPartner.get(player.getUuid());
                Player to = partner == null ? null : MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(partner);
                if (to == null) {
                    player.sendMessage(services.text().message(MessageKey.MSG_NO_REPLY, player));
                    return;
                }
                send(player, to, String.join(" ", context.get(text)));
            }
        }, text);
        return command;
    }

    /** Checks mutes, ignores and the filter, then delivers the message to both players. */
    void send(Player from, Player to, String message) {
        if (services.mutes().isMuted(from.getUuid()) || services.moderation().localMuteUntil(from.getUuid()) > 0) {
            from.sendMessage(services.text().message(MessageKey.CHAT_MUTED_COMMAND, from));
            return;
        }
        if (services.chat().ignoreEnabled() && services.settings().get(to.getUuid()).ignored().contains(from.getUuid())) {
            from.sendMessage(services.text().message(MessageKey.MSG_IGNORED, from));
            return;
        }
        String text = filtered(from, message);
        if (text == null) {
            from.sendMessage(services.text().message(MessageKey.CHAT_BLOCKED, from));
            return;
        }
        from.sendMessage(services.text().message(MessageKey.MSG_TO, from,
                Messages.text("name", to.getUsername()), Messages.text("message", text)));
        to.sendMessage(services.text().message(MessageKey.MSG_FROM, to,
                Messages.text("name", from.getUsername()), Messages.text("message", text)));
        lastPartner.put(from.getUuid(), to.getUuid());
        lastPartner.put(to.getUuid(), from.getUuid());
    }

    /** The message after censoring, or {@code null} if the filter blocks it. */
    private String filtered(Player from, String message) {
        ChatConfig config = services.chat();
        if (!config.filter().enabled() || services.has(from, ChatPermissions.BYPASS_FILTER)) {
            return message;
        }
        List<ChatFilter.Finding> findings = services.filter().get().check(message);
        List<ChatFilter.Finding> censor = new ArrayList<>();
        for (ChatFilter.Finding finding : findings) {
            ChatConfig.Rule rule = config.filter().rules().get(finding.category());
            boolean link = finding.start() < 0;
            if (rule.actions().contains(ChatConfig.Action.BLOCK) || (link && rule.actions().contains(ChatConfig.Action.CENSOR))) {
                return null;
            }
            if (rule.actions().contains(ChatConfig.Action.CENSOR)) {
                censor.add(finding);
            }
        }
        return censor.isEmpty() ? message : ChatFilter.censor(message, censor, config.filter().censorCharacter());
    }
}
