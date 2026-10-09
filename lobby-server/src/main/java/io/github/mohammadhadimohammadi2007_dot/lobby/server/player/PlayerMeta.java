package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import java.util.Map;

/**
 * Rank information about a player, used by the chat, tab list and scoreboard in later phases.
 *
 * @param prefix       rank prefix in legacy or MiniMessage format, empty if none
 * @param suffix       rank suffix, empty if none
 * @param primaryGroup the player's main group, e.g. {@code default}
 * @param meta         extra key/value meta set in the permission plugin
 * @param weight       the weight of the primary group (LuckPerms' {@code weight.<n>}), 0 if it has none;
 *                     higher means a more important rank, which the tab list sorts first
 */
public record PlayerMeta(String prefix, String suffix, String primaryGroup, Map<String, String> meta, int weight) {

    /** Meta for a player without any rank information. */
    public static final PlayerMeta EMPTY = new PlayerMeta("", "", "default", Map.of(), 0);

    public PlayerMeta {
        meta = Map.copyOf(meta);
    }

    /** Meta of a group without a weight. */
    public PlayerMeta(String prefix, String suffix, String primaryGroup, Map<String, String> meta) {
        this(prefix, suffix, primaryGroup, meta, 0);
    }
}
