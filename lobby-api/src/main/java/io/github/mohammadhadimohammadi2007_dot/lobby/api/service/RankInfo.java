package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import java.util.Map;

/**
 * A player's rank from the permission plugin.
 *
 * @param prefix       rank prefix (MiniMessage or {@code &c} colors), empty if none
 * @param suffix       rank suffix, empty if none
 * @param primaryGroup e.g. {@code default}
 * @param meta         other meta values of the player
 * @param weight       the primary group's weight, 0 if none; higher is a more important rank
 */
public record RankInfo(String prefix, String suffix, String primaryGroup, Map<String, String> meta, int weight) {

    public RankInfo {
        meta = Map.copyOf(meta);
    }
}
