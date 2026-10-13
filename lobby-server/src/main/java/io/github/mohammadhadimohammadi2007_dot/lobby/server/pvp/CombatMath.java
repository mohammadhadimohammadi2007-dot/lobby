package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import net.minestom.server.coordinate.Vec;

/**
 * The numbers of a hit, for both combat modes. Pure: everything it needs is passed in, so both modes and
 * mixed fights are unit-tested without a server.
 */
public final class CombatMath {

    /** Ticks per second. */
    private static final double TICKS = 20;
    private static final double CRITICAL = 1.5;
    /** At least this charged for a critical hit, a sweep or the sprint knockback in modern combat. */
    private static final double FULL_CHARGE = 0.9;
    private static final double LEGACY_SHARPNESS_PER_LEVEL = 1.25;
    private static final double MAX_ARMOR = 20;
    private static final double ARMOR_DIVISOR = 25;
    private static final int MAX_PROTECTION = 20;
    /** Knockback of every hit, in blocks per tick: 0.4 to the side and 0.4 up (1.8 and modern). */
    static final double BASE_KNOCKBACK = 0.4;
    /** Extra knockback per level of Knockback and for sprinting, in blocks per tick. */
    static final double EXTRA_KNOCKBACK = 0.5;
    private static final double LEGACY_EXTRA_UP = 0.1;

    private CombatMath() {
    }

    /**
     * What the attacker is doing at the moment of the hit.
     *
     * @param charge    modern only: how charged the swing is, 0 to 1 ({@link #attackStrength}); 1 in 1.8
     * @param falling   moving down, not on the ground (a critical hit)
     */
    public record Swing(CombatMode mode, Weapon weapon, double charge, boolean falling, boolean sprinting,
                        boolean onGround) {
    }

    /**
     * The outcome of one hit.
     *
     * @param damage          health points the defender loses, after armor
     * @param critical        a critical hit (the client shows the stars)
     * @param sweep           modern sword sweep: nearby players take {@link #sweepDamage} too
     * @param extraKnockback  levels of extra knockback (Knockback enchantment, sprinting)
     */
    public record Hit(double damage, boolean critical, boolean sweep, int extraKnockback) {
    }

    /** How charged a modern swing is: 0 just after the last one, 1 once the weapon's cooldown has passed. */
    public static double attackStrength(long ticksSinceLastSwing, double attackSpeed) {
        double cooldownTicks = TICKS / attackSpeed;
        return Math.clamp((ticksSinceLastSwing + 0.5) / cooldownTicks, 0, 1);
    }

    /** The damage and effects of a hit on a defender wearing {@code armor}. */
    public static Hit hit(Swing swing, Armor armor) {
        Weapon weapon = swing.weapon();
        double damage;
        boolean critical;
        boolean sweep = false;
        int extraKnockback = weapon.knockback();
        if (swing.mode() == CombatMode.LEGACY) {
            critical = swing.falling() && !swing.onGround();
            damage = weapon.damage() * (critical ? CRITICAL : 1) + weapon.sharpness() * LEGACY_SHARPNESS_PER_LEVEL;
            if (swing.sprinting()) {
                extraKnockback++;
            }
        } else {
            double charge = swing.charge();
            boolean charged = charge > FULL_CHARGE;
            critical = charged && swing.falling() && !swing.onGround() && !swing.sprinting();
            double base = weapon.damage() * (0.2 + charge * charge * 0.8) * (critical ? CRITICAL : 1);
            damage = base + modernSharpness(weapon.sharpness()) * charge;
            sweep = weapon.sword() && charged && !critical && !swing.sprinting() && swing.onGround();
            if (swing.sprinting() && charged) {
                extraKnockback++;
            }
        }
        return new Hit(reduce(swing.mode(), damage, armor), critical, sweep, extraKnockback);
    }

    /** Damage of a sweep to the players next to the one who was hit, before their armor. */
    public static double sweepDamage(Weapon weapon, double mainDamage) {
        double ratio = weapon.sweeping() == 0 ? 0 : weapon.sweeping() / (weapon.sweeping() + 1.0);
        return 1 + ratio * mainDamage;
    }

    /** Damage after armor and Protection, by the attacker's mode. */
    public static double reduce(CombatMode mode, double damage, Armor armor) {
        double afterArmor;
        if (mode == CombatMode.LEGACY) {
            // 1.8: 4% less per armor point.
            afterArmor = damage * (ARMOR_DIVISOR - Math.min(MAX_ARMOR, armor.points())) / ARMOR_DIVISOR;
        } else {
            // 1.9+: strong hits pierce armor, unless it is tough.
            double effective = Math.clamp(armor.points() - damage / (2 + armor.toughness() / 4), armor.points() * 0.2,
                    MAX_ARMOR);
            afterArmor = damage * (1 - effective / ARMOR_DIVISOR);
        }
        // Protection: 4% less per level, at most 80%.
        return afterArmor * (1 - Math.min(MAX_PROTECTION, armor.protection()) / ARMOR_DIVISOR);
    }

    /** Extra damage of Sharpness since 1.9. */
    static double modernSharpness(int level) {
        return level == 0 ? 0 : 0.5 * level + 0.5;
    }

    /**
     * The velocity a 1.8 hit gives the defender, in blocks per second: pushed away from the attacker and up, plus
     * the extra knockback in the direction the attacker looks.
     *
     * @param dx         defender x minus attacker x
     * @param dz         defender z minus attacker z
     * @param attackerYaw in degrees
     */
    public static Vec legacyKnockback(double dx, double dz, float attackerYaw, int extraKnockback) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        double x = 0;
        double z = 0;
        if (distance > 1.0E-4) {
            x = dx / distance * BASE_KNOCKBACK;
            z = dz / distance * BASE_KNOCKBACK;
        }
        double y = BASE_KNOCKBACK;
        if (extraKnockback > 0) {
            double yaw = Math.toRadians(attackerYaw);
            x += -Math.sin(yaw) * extraKnockback * EXTRA_KNOCKBACK;
            z += Math.cos(yaw) * extraKnockback * EXTRA_KNOCKBACK;
            y += LEGACY_EXTRA_UP;
        }
        return new Vec(x, y, z).mul(TICKS);
    }
}
