package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import net.minestom.server.entity.Player;

/** Permission checks and ranks, from LuckPerms (or the operators list without it). Fast, any thread. */
public interface Permissions {

    /** True if the player has the permission (operators have all, unless the config says otherwise). */
    boolean has(Player player, String permission);

    /** The player's rank information. */
    RankInfo rank(Player player);
}
