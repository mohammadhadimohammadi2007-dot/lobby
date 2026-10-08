package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * How to compute one placeholder, for example {@code %server_online%}.
 *
 * <p>There are three kinds, chosen for performance with hundreds of players:
 * <ul>
 *   <li>{@link Global}: same value for everyone; computed once and cached for {@code cacheTime}.</li>
 *   <li>{@link PerPlayer}: one value per player; cached per player until {@code cacheTime} passes or the
 *       player's data changes (rank change, quit). {@code cacheTime} {@code null} means "until it changes".</li>
 *   <li>{@link Relational}: depends on who is looking ("viewer") and who it is about ("target");
 *       never cached, so only use it where really needed.</li>
 * </ul>
 * Values are text. A {@code formatted} value may contain colors (MiniMessage or legacy {@code &c}) and
 * should only come from trusted sources such as config files or LuckPerms. Anything a player can choose
 * (names, chat) must be {@code formatted = false}: it is shown exactly as written.
 */
public sealed interface Placeholder {

    /** True if the value may contain colors and formatting. */
    boolean formatted();

    /** Same value for everyone. */
    record Global(Supplier<String> value, Duration cacheTime, boolean formatted) implements Placeholder {
        public Global {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(cacheTime, "cacheTime");
        }
    }

    /** One value per player. {@code cacheTime} {@code null}: cached until the player's data changes. */
    record PerPlayer(Function<Player, String> value, Duration cacheTime, boolean formatted) implements Placeholder {
        public PerPlayer {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Depends on the viewer and the target. Not cached. */
    record Relational(BiFunction<Player, Player, String> value, boolean formatted) implements Placeholder {
        public Relational {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A global plain-text placeholder cached for {@code cacheTime}. */
    static Placeholder global(Duration cacheTime, Supplier<String> value) {
        return new Global(value, cacheTime, false);
    }

    /** A per-player plain-text placeholder cached until the player's data changes. */
    static Placeholder player(Function<Player, String> value) {
        return new PerPlayer(value, null, false);
    }

    /** A per-player plain-text placeholder cached for {@code cacheTime}, for fast-changing values like ping. */
    static Placeholder player(Duration cacheTime, Function<Player, String> value) {
        return new PerPlayer(value, cacheTime, false);
    }

    /** A per-player placeholder whose value may contain colors (for example a rank prefix). */
    static Placeholder formattedPlayer(Function<Player, String> value) {
        return new PerPlayer(value, null, true);
    }
}
