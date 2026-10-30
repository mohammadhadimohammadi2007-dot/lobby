package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Cosmetics;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A player is about to put on or take off a cosmetic (in the menu, or through the API). Cancelling it keeps what they
 * had. Tick thread.
 */
public final class CosmeticChangeEvent extends CancellableLobbyEvent {

    private final Cosmetics.Kind kind;
    private final @Nullable String previous;
    private final @Nullable String next;

    public CosmeticChangeEvent(Player player, Cosmetics.Kind kind, @Nullable String previous, @Nullable String next) {
        super(player);
        this.kind = kind;
        this.previous = previous;
        this.next = next;
    }

    public Cosmetics.Kind kind() {
        return kind;
    }

    /** The id used until now, or {@code null}. */
    public @Nullable String previous() {
        return previous;
    }

    /** The id put on, or {@code null} when the cosmetic is taken off. */
    public @Nullable String next() {
        return next;
    }
}
