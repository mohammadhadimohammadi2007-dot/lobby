package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter.ChatFilter;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies the word lists and link/IP detection, with the actions from chat.yml for each kind of finding:
 * block, censor, warn and notify staff. Hits add violation points (see {@link AutoMuter}).
 */
public final class FilterStage implements ChatStage {

    private final ChatServices services;
    private final AutoMuter autoMuter;

    public FilterStage(ChatServices services) {
        this.services = services;
        this.autoMuter = new AutoMuter(services);
    }

    @Override
    public String name() {
        return "filter";
    }

    @Override
    public boolean enabled(ChatConfig config) {
        return config.filter().enabled();
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        Player sender = message.sender();
        if (services.has(sender, ChatPermissions.BYPASS_FILTER)) {
            return Result.CONTINUE;
        }
        List<ChatFilter.Finding> findings = services.filter().get().check(message.text());
        if (findings.isEmpty()) {
            return Result.CONTINUE;
        }

        boolean block = false;
        boolean warn = false;
        boolean linkWarning = false;
        int points = 0;
        Set<ChatFilter.Category> seen = EnumSet.noneOf(ChatFilter.Category.class);
        List<ChatFilter.Finding> toCensor = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (ChatFilter.Finding finding : findings) {
            ChatConfig.Rule rule = config.filter().rules().get(finding.category());
            boolean link = finding.category() == ChatFilter.Category.LINKS || finding.category() == ChatFilter.Category.IPS;
            if (rule.actions().contains(ChatConfig.Action.BLOCK)
                    || (link && rule.actions().contains(ChatConfig.Action.CENSOR))) {
                block = true;
            } else if (rule.actions().contains(ChatConfig.Action.CENSOR)) {
                toCensor.add(finding);
            }
            if (rule.actions().contains(ChatConfig.Action.WARN)) {
                warn |= !link;
                linkWarning |= link;
            }
            if (rule.actions().contains(ChatConfig.Action.NOTIFY)) {
                notifyStaff(message, finding);
            }
            if (seen.add(finding.category())) {
                points += rule.points();
            }
            reasons.add(categoryName(finding.category()) + " '" + finding.detail() + "'");
        }

        autoMuter.addPoints(message, points, config.filter().autoMute());
        if (warn && sender != null) {
            sender.sendMessage(services.text().message(MessageKey.CHAT_WARNING, sender));
        }
        if (linkWarning && sender != null) {
            sender.sendMessage(services.text().message(MessageKey.CHAT_LINK_WARNING, sender));
        }
        String reason = String.join(", ", reasons);
        if (block) {
            return Result.block(reason, services.text().message(MessageKey.CHAT_BLOCKED, sender));
        }
        if (!toCensor.isEmpty()) {
            message.modify(ChatFilter.censor(message.text(), toCensor, config.filter().censorCharacter()), "censored " + reason);
        }
        return Result.CONTINUE;
    }

    private void notifyStaff(ChatMessage message, ChatFilter.Finding finding) {
        services.tellStaff(ChatPermissions.NOTIFY, services.text().message(MessageKey.CHAT_STAFF_NOTIFY,
                Messages.text("player", message.senderName()),
                Messages.text("category", categoryName(finding.category())),
                Messages.text("detail", finding.detail()),
                Messages.text("message", message.original())));
    }

    private static String categoryName(ChatFilter.Category category) {
        return category.configName().replace('-', ' ').toLowerCase(Locale.ROOT);
    }
}
