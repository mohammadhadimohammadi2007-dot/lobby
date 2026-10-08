package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.pipeline.ChatStage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.Punishment;
import net.minestom.server.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Stops messages from muted players: LiteBans mutes and this lobby's automatic mutes. Always on. */
public final class MuteStage implements ChatStage {

    static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ChatServices services;

    public MuteStage(ChatServices services) {
        this.services = services;
    }

    @Override
    public String name() {
        return "mute";
    }

    @Override
    public Result process(ChatMessage message, ChatConfig config) {
        Player sender = message.sender();
        Optional<Punishment> mute = services.mutes().activeMute(message.senderId());
        if (mute.isPresent()) {
            Punishment punishment = mute.get();
            String expires = punishment.permanent()
                    ? services.config().current().messages().template(MessageKey.BAN_NEVER_EXPIRES)
                    : EXPIRY.format(Instant.ofEpochMilli(punishment.untilMillis()));
            return Result.block("muted (LiteBans #" + punishment.id() + ")", services.text().message(MessageKey.CHAT_MUTED,
                    sender, Messages.text("reason", punishment.reason()), Messages.text("expires", expires)));
        }
        long localUntil = services.moderation().localMuteUntil(message.senderId());
        if (localUntil > 0) {
            String reason = services.config().current().messages().template(MessageKey.CHAT_AUTO_MUTE_REASON);
            return Result.block("muted (automatic)", services.text().message(MessageKey.CHAT_MUTED, sender,
                    Messages.text("reason", reason), Messages.text("expires", EXPIRY.format(Instant.ofEpochMilli(localUntil)))));
        }
        return Result.CONTINUE;
    }
}
