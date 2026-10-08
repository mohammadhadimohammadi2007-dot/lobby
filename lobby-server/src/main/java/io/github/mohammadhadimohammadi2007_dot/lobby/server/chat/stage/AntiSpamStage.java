package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPlayerTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam.SpamChecks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.TextNormalizer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.entity.Player;

/**
 * Content checks: too long (blocked), near-duplicate of a recent message (blocked), too many capitals
 * (made lower case), and long runs of the same character (shortened).
 */
public final class AntiSpamStage implements ChatStage {

    private static final double PERCENT = 100.0;

    private final ChatServices services;

    public AntiSpamStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "anti-spam";
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        Player sender = message.sender();
        if (message.text().length() > config.maxLength()) {
            return Result.block("too long", services.text().message(MessageKey.CHAT_TOO_LONG, sender,
                    Messages.text("max", config.maxLength())));
        }
        if (services.has(sender, ChatPermissions.BYPASS_SPAM)) {
            return Result.CONTINUE;
        }
        ChatConfig.AntiSpam spam = config.antiSpam();
        ChatPlayerTracker.State state = services.players().state(message.senderId());
        String normalized = TextNormalizer.forFilter(message.text()).text();
        if (spam.duplicateCheck()
                && SpamChecks.isNearDuplicate(normalized, state.recent(), spam.duplicateSimilarityPercent() / PERCENT)) {
            return Result.block("duplicate", services.text().message(MessageKey.CHAT_DUPLICATE, sender));
        }
        state.remember(normalized, spam.duplicateHistory());

        if (spam.maxCapsPercent() < 100 && SpamChecks.casedLetters(message.text()) >= spam.capsMinLength()
                && SpamChecks.capsPercent(message.text()) > spam.maxCapsPercent()) {
            message.modify(SpamChecks.quieten(message.text()), "too many capitals");
        }
        if (spam.maxRepeatedCharacters() > 0) {
            message.modify(SpamChecks.limitRepeats(message.text(), spam.maxRepeatedCharacters()), "repeated characters");
        }
        return Result.CONTINUE;
    }
}
