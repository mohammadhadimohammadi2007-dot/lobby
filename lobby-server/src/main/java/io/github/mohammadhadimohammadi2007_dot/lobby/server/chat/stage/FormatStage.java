package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPlayerTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.MentionFinder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Picks the chat format (by primary group or {@code lobby.chat.format.<name>}), gathers the sender's rank
 * data, and finds mentioned players.
 */
public final class FormatStage implements ChatStage {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final String DEFAULT_FORMAT = "default";

    private final ChatServices services;

    public FormatStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "format";
    }

    @Override
    public boolean appliesToRelayed() {
        return true;
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        Player sender = message.sender();
        if (sender != null) {
            message.senderMeta(services.permissions().meta(sender));
            message.styling(services.has(sender, ChatPermissions.FORMATTING));
            message.emojis(services.has(sender, ChatPermissions.EMOJI));
        } else {
            message.emojis(true);
        }
        String group = message.senderMeta().primaryGroup().toLowerCase(Locale.ROOT);
        message.format(select(config.formats(), group,
                sender == null ? permission -> false : permission -> services.has(sender, permission)));
        if (config.mentions().enabled()) {
            findMentions(message, config, sender);
        }
        return Result.CONTINUE;
    }

    /**
     * The first format (highest priority) whose groups contain {@code group} or whose permission the sender
     * has; otherwise the format named "default", otherwise the lowest one.
     */
    static ChatConfig.Format select(List<ChatConfig.Format> formats, String group, Predicate<String> hasPermission) {
        ChatConfig.Format fallback = null;
        for (ChatConfig.Format format : formats) {
            if (format.groups().contains(group) || hasPermission.test(ChatPermissions.FORMAT_PREFIX + format.name())) {
                return format;
            }
            if (format.name().equals(DEFAULT_FORMAT)) {
                fallback = format;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        return formats.isEmpty()
                ? new ChatConfig.Format(DEFAULT_FORMAT, 0, Set.of(), "<name>: <message>", "", "", List.of(), "")
                : formats.getLast();
    }

    private void findMentions(ChatMessage message, ChatConfig config, @Nullable Player sender) {
        MentionFinder.Result found = MentionFinder.find(message.text(), services.players().onlineByName(),
                message.senderId());
        message.mentionedNames().putAll(found.mentioned());
        message.mentionEveryone(found.everyone() && sender != null
                && services.has(sender, ChatPermissions.MENTION_EVERYONE));

        if (sender != null && (!found.mentioned().isEmpty() || message.mentionEveryone())) {
            ChatPlayerTracker.State state = services.players().state(message.senderId());
            long now = System.nanoTime();
            long cooldown = config.mentions().cooldownSeconds() * NANOS_PER_SECOND;
            if (state.lastMentionNanos() != 0 && now - state.lastMentionNanos() < cooldown) {
                message.notifyMentions(false);
            } else {
                state.lastMentionNanos(now);
            }
        }
    }
}
