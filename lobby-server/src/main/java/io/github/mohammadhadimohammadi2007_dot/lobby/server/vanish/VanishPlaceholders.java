package io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * {@code %vanish_vanished%} ({@code yes} or {@code no}, about the player) and {@code %vanish_count%} (how many are
 * vanished, for a staff scoreboard).
 */
public final class VanishPlaceholders implements PlaceholderNamespace {

    private static final Duration SHORT = Duration.ofSeconds(1);

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "vanished" -> Placeholder.player(SHORT, player -> Vanished.is(player) ? "yes" : "no");
            case "count" -> Placeholder.global(SHORT, () -> String.valueOf(Vanished.count()));
            default -> null;
        };
    }
}
