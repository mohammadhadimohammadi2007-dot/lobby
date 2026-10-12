package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A chat message, after the lobby's mute, cooldown, anti-spam and filter checks and before it is formatted
 * and sent. Listeners may change the text or cancel the message; a cancelled message is logged as blocked
 * like one the filter stopped. Runs on the chat thread, one message at a time: keep it quick.
 *
 * <p>The text is what other players will see, written by a player: it is never read as MiniMessage.
 */
public final class LobbyChatEvent extends CancellableLobbyEvent {

    private final String original;
    private volatile String message;
    private volatile @Nullable Component cancelFeedback;

    public LobbyChatEvent(Player player, String original, String message) {
        super(player);
        this.original = original;
        this.message = message;
    }

    /** Exactly what the player typed. */
    public String original() {
        return original;
    }

    /** The text that will be sent (after the filter's changes). */
    public String message() {
        return message;
    }

    /** Replaces the text that will be sent. */
    public void message(String text) {
        message = Objects.requireNonNull(text, "text");
    }

    /** What the sender is told if the message is cancelled, or {@code null} for nothing. */
    public @Nullable Component cancelFeedback() {
        return cancelFeedback;
    }

    /** Cancels the message and tells the sender why. */
    public void cancel(@Nullable Component feedback) {
        cancelFeedback = feedback;
        setCancelled(true);
    }
}
