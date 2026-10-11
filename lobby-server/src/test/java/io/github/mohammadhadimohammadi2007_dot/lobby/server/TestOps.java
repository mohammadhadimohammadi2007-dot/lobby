package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Operators;

import java.util.Set;

/** Operators for tests: the players with these names. */
public final class TestOps {

    private TestOps() {
    }

    /** Operators by name, ignoring case like player names do. */
    public static Operators named(String... names) {
        Set<String> set = java.util.Arrays.stream(names).map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return player -> set.contains(player.getUsername().toLowerCase(java.util.Locale.ROOT));
    }
}
