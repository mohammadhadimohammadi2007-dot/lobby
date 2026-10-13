package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.ClientCapabilities;

/**
 * How a hit works. Decided by the ATTACKER's client: a 1.8 player clicks fast and expects 1.8 combat, a
 * modern player swings with a cooldown. So a 1.8 player hitting a modern one deals 1.8 damage, and the other way
 * round.
 */
public enum CombatMode {
    /** Minecraft 1.8: no attack cooldown, 1.8 weapon damage, armor and knockback, critical hits when falling. */
    LEGACY,
    /**
     * 1.9 and newer: damage scaled by how charged the swing is, sweep attacks with swords, modern critical hits,
     * armor toughness and knockback. Used for clients from 1.19.4 on (the lobby's "modern" tier).
     */
    MODERN;

    /** The mode for a player's client. */
    public static CombatMode of(ClientCapabilities client) {
        return client.legacy() ? LEGACY : MODERN;
    }
}
