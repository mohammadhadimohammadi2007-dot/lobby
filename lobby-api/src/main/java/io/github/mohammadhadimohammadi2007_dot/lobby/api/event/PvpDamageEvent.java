package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.entity.Player;

/**
 * A player is about to hurt another in the PvP area, after the lobby worked out the damage (weapon, charge,
 * critical hit, armor). Listeners may change the damage or cancel the hit (no damage, no knockback, no combat
 * tag). Tick thread.
 */
public final class PvpDamageEvent extends CancellableLobbyEvent {

    private final Player attacker;
    private final boolean legacyCombat;
    private final boolean critical;
    private volatile double damage;

    /** @param player the player who is hit */
    public PvpDamageEvent(Player player, Player attacker, double damage, boolean critical, boolean legacyCombat) {
        super(player);
        this.attacker = attacker;
        this.damage = damage;
        this.critical = critical;
        this.legacyCombat = legacyCombat;
    }

    /** Who hits. {@link #getPlayer()} is who is hit. */
    public Player attacker() {
        return attacker;
    }

    /** Health points the hit takes (2 = one heart), after armor. */
    public double damage() {
        return damage;
    }

    public void damage(double value) {
        damage = Math.max(0, value);
    }

    public boolean critical() {
        return critical;
    }

    /** True if the attacker plays 1.8 and the hit follows 1.8 rules; false for modern rules. */
    public boolean legacyCombat() {
        return legacyCombat;
    }
}
