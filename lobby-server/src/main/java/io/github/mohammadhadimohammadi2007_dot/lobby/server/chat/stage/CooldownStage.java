package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPermissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatPlayerTracker;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam.TokenBucket;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import net.minestom.server.entity.Player;

/**
 * Time-based limits: chat lock, new-player protection, slow mode, and the per-player rate limit
 * (with per-rank cooldowns from {@code lobby.chat.cooldown.<seconds>}).
 */
public final class CooldownStage implements ChatStage {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final ChatServices services;

    public CooldownStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "cooldown";
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        Player sender = message.sender();
        long now = System.nanoTime();
        if (services.moderation().locked() && !services.has(sender, ChatPermissions.BYPASS_LOCK)) {
            return Result.block("chat locked", services.text().message(MessageKey.CHAT_LOCKED, sender));
        }
        if (services.has(sender, ChatPermissions.BYPASS_SPAM)) {
            return Result.CONTINUE;
        }
        ChatPlayerTracker.State state = services.players().state(message.senderId());
        ChatConfig.AntiSpam spam = config.antiSpam();

        long waitedSeconds = (now - state.joinedNanos()) / NANOS_PER_SECOND;
        if (spam.newPlayerDelaySeconds() > 0 && waitedSeconds < spam.newPlayerDelaySeconds()) {
            return Result.block("new player", services.text().message(MessageKey.CHAT_NEW_PLAYER_WAIT, sender,
                    Messages.text("seconds", spam.newPlayerDelaySeconds() - waitedSeconds)));
        }
        if (spam.requireMove() && !state.moved()) {
            return Result.block("new player has not moved", services.text().message(MessageKey.CHAT_NEW_PLAYER_MOVE, sender));
        }

        int slowmode = services.moderation().slowmodeSeconds();
        if (slowmode > 0 && !services.has(sender, ChatPermissions.BYPASS_SLOWMODE)) {
            long since = (now - state.lastSlowmodeMessageNanos()) / NANOS_PER_SECOND;
            if (state.lastSlowmodeMessageNanos() != 0 && since < slowmode) {
                return Result.block("slow mode", services.text().message(MessageKey.CHAT_SLOWMODE, sender,
                        Messages.text("seconds", slowmode - since)));
            }
            state.lastSlowmodeMessageNanos(now);
        }

        int cooldown = cooldownFor(sender, state, spam.cooldownSeconds());
        TokenBucket bucket = state.bucket(spam.burst(), cooldown, now);
        if (!bucket.tryConsume(now)) {
            long seconds = (long) Math.ceil(bucket.secondsUntilNext(now));
            return Result.block("cooldown", services.text().message(MessageKey.CHAT_COOLDOWN, sender,
                    Messages.text("seconds", Math.max(1, seconds))));
        }
        return Result.CONTINUE;
    }

    /**
     * The lowest {@code lobby.chat.cooldown.<seconds>} the player has, or the default. Worked out once per
     * player and cached until their rank changes or the config is reloaded.
     */
    private int cooldownFor(Player sender, ChatPlayerTracker.State state, int defaultSeconds) {
        if (state.cooldownSeconds() >= 0) {
            return state.cooldownSeconds();
        }
        int seconds = defaultSeconds;
        for (int candidate = 0; candidate < defaultSeconds; candidate++) {
            if (services.permissions().hasPermission(sender, ChatPermissions.COOLDOWN_PREFIX + candidate)) {
                seconds = candidate;
                break;
            }
        }
        state.cooldownSeconds(seconds);
        return seconds;
    }
}
