package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

/**
 * How much the value of a text can differ between the players reading it. Ordered from "the same for
 * everybody" to "different for every pair of players", so the scope of a whole text is the highest
 * scope of the placeholders in it.
 *
 * <p>This decides how often something has to be rendered. A hologram line such as
 * {@code %group_online_bedwars% playing} is {@link #GLOBAL}: it changes over time, but at any moment it
 * is the same for all 200 players, so it is built once and the same packet is sent to everyone. Only
 * {@link #PER_PLAYER} and {@link #RELATIONAL} text has to be built per player.
 */
public enum PlaceholderScope {

    /** No placeholders at all: it never changes. */
    STATIC,
    /** Only placeholders that are the same for every player, such as a player count. */
    GLOBAL,
    /** At least one placeholder that depends on who the text is about, such as their rank. */
    PER_PLAYER,
    /** At least one placeholder that depends on both the reader and who the text is about. */
    RELATIONAL;

    /** True if this text has to be rendered for each player separately. */
    public boolean perPlayer() {
        return this == PER_PLAYER || this == RELATIONAL;
    }

    /** True if the value can change while the server runs, so it has to be rebuilt now and then. */
    public boolean changesOverTime() {
        return this != STATIC;
    }

    /** The higher of two scopes. */
    public PlaceholderScope and(PlaceholderScope other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
