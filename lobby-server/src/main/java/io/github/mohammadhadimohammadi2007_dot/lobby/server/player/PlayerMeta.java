package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import java.util.Map;

/**
 * Rank information about a player, used by the chat, tab list and scoreboard in later phases.
 *
 * @param prefix       rank prefix in legacy or MiniMessage format, empty if none
 * @param suffix       rank suffix, empty if none
 * @param primaryGroup the player's main group, e.g. {@code default}
 * @param meta         extra key/value meta set in the permission plugin
 */
public record PlayerMeta(String prefix, String suffix, String primaryGroup, Map<String, String> meta) {

    /** Meta for a player without any rank information. */
    public static final PlayerMeta EMPTY = new PlayerMeta("", "", "default", Map.of());

    public PlayerMeta {
        meta = Map.copyOf(meta);
    }
}
