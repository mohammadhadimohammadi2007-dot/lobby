package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

/** The kinds of map the lobby can load. */
public enum WorldFormat {
    /** A single {@code .polar} file. */
    POLAR,
    /** A vanilla world folder containing {@code region/}. */
    ANVIL,
    /** No map found. A small flat platform is generated instead. */
    FLAT_FALLBACK
}
