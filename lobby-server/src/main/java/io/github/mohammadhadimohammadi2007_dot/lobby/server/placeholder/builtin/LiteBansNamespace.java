package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.Punishment;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * {@code %litebans_muted%} (yes/no), {@code %litebans_mute_reason%} and {@code %litebans_mute_expires%}.
 * Read from the in-memory mute cache, so they are cheap; cached for one second because mutes expire.
 */
final class LiteBansNamespace implements PlaceholderNamespace {

    static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Duration CACHE = Duration.ofSeconds(1);

    private final MuteService mutes;
    private final ConfigManager config;

    LiteBansNamespace(MuteService mutes, ConfigManager config) {
        this.mutes = mutes;
        this.config = config;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "muted" -> Placeholder.player(CACHE, player -> BuiltinPlaceholders.yesNo(mutes.isMuted(player.getUuid())));
            case "mute_reason" -> Placeholder.player(CACHE,
                    player -> mutes.activeMute(player.getUuid()).map(Punishment::reason).orElse(""));
            case "mute_expires" -> Placeholder.player(CACHE, player -> expires(mutes.activeMute(player.getUuid())));
            default -> null;
        };
    }

    private String expires(Optional<Punishment> mute) {
        if (mute.isEmpty()) {
            return "";
        }
        return mute.get().permanent()
                ? config.current().messages().template(MessageKey.BAN_NEVER_EXPIRES)
                : EXPIRY_FORMAT.format(Instant.ofEpochMilli(mute.get().untilMillis()));
    }
}
