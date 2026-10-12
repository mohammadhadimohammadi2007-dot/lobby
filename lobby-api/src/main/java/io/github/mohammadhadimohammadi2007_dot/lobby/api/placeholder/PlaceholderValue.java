package io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder;

import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * How one placeholder is computed. Pick the narrowest scope, because it decides how much work a text is with
 * hundreds of players: a hologram line with only {@link #global} placeholders is built once and the same
 * packet goes to everyone; one {@link #perPlayer} placeholder makes it be built for each player.
 *
 * <ul>
 *   <li>{@link #constant}: never changes while the addon runs.</li>
 *   <li>{@link #global}: the same for everyone, computed at most once per {@code cacheTime}.</li>
 *   <li>{@link #perPlayer}: about the player, cached per player for {@code cacheTime}.</li>
 *   <li>{@link #relational}: depends on who looks ({@code viewer}) and who it is about ({@code target});
 *       never cached, so only where really needed (for example in chat formats).</li>
 * </ul>
 * Values are plain text unless made with {@code formatted = true}: then they may contain MiniMessage or
 * {@code &c} colors. Never mark text a player can choose (names, chat) as formatted.
 *
 * <p>The functions run on the thread that renders the text (display, chat or a virtual thread): keep them
 * quick and never block. Read from your own cache and refresh it with the scheduler.
 */
public sealed interface PlaceholderValue {

    /** True if the value may contain colors. */
    boolean formatted();

    /** A value that never changes. */
    record Constant(String value, boolean formatted) implements PlaceholderValue {
        public Constant {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Same for everyone. */
    record Global(Supplier<String> value, Duration cacheTime, boolean formatted) implements PlaceholderValue {
        public Global {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(cacheTime, "cacheTime");
        }
    }

    /** One value per player. */
    record PerPlayer(Function<Player, String> value, Duration cacheTime, boolean formatted) implements PlaceholderValue {
        public PerPlayer {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(cacheTime, "cacheTime");
        }
    }

    /** Depends on the viewer and the target. */
    record Relational(BiFunction<Player, Player, String> value, boolean formatted) implements PlaceholderValue {
        public Relational {
            Objects.requireNonNull(value, "value");
        }
    }

    static PlaceholderValue constant(String value) {
        return new Constant(value, false);
    }

    static PlaceholderValue global(Duration cacheTime, Supplier<String> value) {
        return new Global(value, cacheTime, false);
    }

    static PlaceholderValue perPlayer(Duration cacheTime, Function<Player, String> value) {
        return new PerPlayer(value, cacheTime, false);
    }

    static PlaceholderValue relational(BiFunction<Player, Player, String> value) {
        return new Relational(value, false);
    }
}
