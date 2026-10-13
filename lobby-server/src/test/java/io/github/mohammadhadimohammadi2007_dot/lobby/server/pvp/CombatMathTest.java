package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Both combat modes, and what a mixed fight looks like. */
class CombatMathTest {

    private static final double DELTA = 1.0E-6;

    @BeforeAll
    static void loadItems() {
        // Item prototypes (their default attribute modifiers) are bound when Minestom starts.
        MinecraftServer.init();
    }

    private static CombatMath.Swing swing(CombatMode mode, Weapon weapon, double charge) {
        return new CombatMath.Swing(mode, weapon, charge, false, false, true);
    }

    @Test
    void weaponsHaveTheirModernDamageFromTheItemsAndTheir18DamageInLegacyMode() {
        // Modern values come from the items' own attribute modifiers (Minestom's registry).
        Weapon modernSword = Weapon.of(ItemStack.of(Material.DIAMOND_SWORD), CombatMode.MODERN);
        assertEquals(7, modernSword.damage(), DELTA);
        assertEquals(1.6, modernSword.attackSpeed(), DELTA);
        assertTrue(modernSword.sword());
        assertEquals(9, Weapon.of(ItemStack.of(Material.DIAMOND_AXE), CombatMode.MODERN).damage(), DELTA);

        // 1.8: sword 4 + material, axe 3 + material, plus the hand's 1.
        assertEquals(8, Weapon.of(ItemStack.of(Material.DIAMOND_SWORD), CombatMode.LEGACY).damage(), DELTA);
        assertEquals(5, Weapon.of(ItemStack.of(Material.WOODEN_SWORD), CombatMode.LEGACY).damage(), DELTA);
        assertEquals(5, Weapon.of(ItemStack.of(Material.GOLDEN_SWORD), CombatMode.LEGACY).damage(), DELTA);
        assertEquals(7, Weapon.of(ItemStack.of(Material.DIAMOND_AXE), CombatMode.LEGACY).damage(), DELTA);
        assertEquals(1, Weapon.of(ItemStack.of(Material.DIAMOND_HOE), CombatMode.LEGACY).damage(), DELTA);
        assertEquals(1, Weapon.of(ItemStack.AIR, CombatMode.LEGACY).damage(), DELTA);
        assertEquals(1, Weapon.of(ItemStack.of(Material.STICK), CombatMode.MODERN).damage(), DELTA);
        assertNull(Weapon.legacyDamage(Material.TRIDENT), "items without a 1.8 value keep their modern damage");
    }

    @Test
    void enchantmentsAreRead() {
        ItemStack sword = ItemStack.of(Material.IRON_SWORD).with(DataComponents.ENCHANTMENTS,
                new EnchantmentList(Enchantment.SHARPNESS, 2).with(Enchantment.KNOCKBACK, 1));
        Weapon weapon = Weapon.of(sword, CombatMode.LEGACY);
        assertEquals(2, weapon.sharpness());
        assertEquals(1, weapon.knockback());
    }

    @Test
    void legacyHasNoCooldownAndCritsWhenFalling() {
        Weapon sword = new Weapon(8, 1.6, 0, 0, 0, true);
        assertEquals(8, CombatMath.hit(swing(CombatMode.LEGACY, sword, 1), Armor.NONE).damage(), DELTA);
        // A 1.8 player clicking again at once still deals full damage: only the hurt timer limits it.
        assertEquals(8, CombatMath.hit(swing(CombatMode.LEGACY, sword, 0), Armor.NONE).damage(), DELTA);

        CombatMath.Hit falling = CombatMath.hit(new CombatMath.Swing(CombatMode.LEGACY, sword, 1, true, true, false),
                Armor.NONE);
        assertTrue(falling.critical(), "1.8 crits even while sprinting");
        assertEquals(12, falling.damage(), DELTA);
        assertFalse(falling.sweep());
        assertEquals(1, falling.extraKnockback(), "sprinting adds knockback in 1.8");

        Weapon sharp = new Weapon(8, 1.6, 2, 0, 0, true);
        assertEquals(10.5, CombatMath.hit(swing(CombatMode.LEGACY, sharp, 1), Armor.NONE).damage(), DELTA);
    }

    @Test
    void modernDamageDependsOnTheCharge() {
        Weapon sword = new Weapon(7, 1.6, 0, 0, 0, true);
        assertEquals(7, CombatMath.hit(swing(CombatMode.MODERN, sword, 1), Armor.NONE).damage(), DELTA);
        assertEquals(7 * 0.2, CombatMath.hit(swing(CombatMode.MODERN, sword, 0), Armor.NONE).damage(), DELTA);
        assertEquals(7 * (0.2 + 0.25 * 0.8), CombatMath.hit(swing(CombatMode.MODERN, sword, 0.5), Armor.NONE).damage(),
                DELTA);

        // A sword needs 12.5 ticks (20 / 1.6) to charge fully.
        assertEquals(0.04, CombatMath.attackStrength(0, 1.6), DELTA);
        assertEquals(1, CombatMath.attackStrength(12, 1.6), DELTA);
        assertEquals(1, CombatMath.attackStrength(100, 1.6), DELTA);
    }

    @Test
    void modernCritsAndSweepsNeedAFullSwing() {
        Weapon sword = new Weapon(7, 1.6, 0, 0, 0, true);
        CombatMath.Hit crit = CombatMath.hit(new CombatMath.Swing(CombatMode.MODERN, sword, 1, true, false, false),
                Armor.NONE);
        assertTrue(crit.critical());
        assertEquals(10.5, crit.damage(), DELTA);
        assertFalse(crit.sweep(), "a critical hit does not sweep");

        assertFalse(CombatMath.hit(new CombatMath.Swing(CombatMode.MODERN, sword, 0.5, true, false, false),
                Armor.NONE).critical(), "half charged: no crit");
        assertFalse(CombatMath.hit(new CombatMath.Swing(CombatMode.MODERN, sword, 1, true, true, false),
                Armor.NONE).critical(), "sprinting: no crit since 1.9");

        assertTrue(CombatMath.hit(swing(CombatMode.MODERN, sword, 1), Armor.NONE).sweep());
        Weapon axe = new Weapon(9, 1, 0, 0, 0, false);
        assertFalse(CombatMath.hit(swing(CombatMode.MODERN, axe, 1), Armor.NONE).sweep(), "only swords sweep");
        assertEquals(1, CombatMath.sweepDamage(sword, 7), DELTA);
        assertEquals(1 + 0.75 * 8, CombatMath.sweepDamage(new Weapon(7, 1.6, 0, 0, 3, true), 8), DELTA);
    }

    @Test
    void armorFollowsTheAttackersMode() {
        Armor diamond = new Armor(20, 8, 0);
        // 1.8: 4% per point, so a full diamond set blocks 80%.
        assertEquals(10 * 0.2, CombatMath.reduce(CombatMode.LEGACY, 10, diamond), DELTA);
        // Modern: 20 - 10 / (2 + 8/4) = 17.5 effective points.
        assertEquals(10 * (1 - 17.5 / 25), CombatMath.reduce(CombatMode.MODERN, 10, diamond), DELTA);
        // Protection IV on all four pieces: 16 levels, 64% less.
        assertEquals(10 * (1 - 16 / 25.0), CombatMath.reduce(CombatMode.LEGACY, 10, new Armor(0, 0, 16)), DELTA);
        assertEquals(10 * 0.2, CombatMath.reduce(CombatMode.LEGACY, 10, new Armor(0, 0, 40)), DELTA, "at most 80%");
    }

    @Test
    void aMixedFightUsesTheAttackersRules() {
        // The same diamond sword: a 1.8 player spam-clicking always deals 8, a modern one only 1.4 without waiting.
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD);
        double legacySpam = CombatMath.hit(swing(CombatMode.LEGACY, Weapon.of(item, CombatMode.LEGACY), 0),
                Armor.NONE).damage();
        double modernSpam = CombatMath.hit(swing(CombatMode.MODERN, Weapon.of(item, CombatMode.MODERN),
                CombatMath.attackStrength(0, 1.6)), Armor.NONE).damage();
        assertEquals(8, legacySpam, DELTA);
        assertTrue(modernSpam < 1.5, "modern spam deals " + modernSpam);
        // Waiting for the cooldown, the modern player gets close: 7.
        double modernFull = CombatMath.hit(swing(CombatMode.MODERN, Weapon.of(item, CombatMode.MODERN), 1),
                Armor.NONE).damage();
        assertEquals(7, modernFull, DELTA);
    }

    @Test
    void legacyKnockbackPushesAwayAndUp() {
        Vec push = CombatMath.legacyKnockback(3, 0, 0, 0);
        assertEquals(8, push.x(), DELTA);
        assertEquals(8, push.y(), DELTA);
        assertEquals(0, push.z(), DELTA);

        // Yaw 0 looks towards +z; one level of extra knockback adds 0.5 blocks per tick that way, and 0.1 up.
        Vec extra = CombatMath.legacyKnockback(0, 3, 0, 1);
        assertEquals(0, extra.x(), DELTA);
        assertEquals((0.4 + 0.5) * 20, extra.z(), DELTA);
        assertEquals((0.4 + 0.1) * 20, extra.y(), DELTA);
    }

    @Test
    void theHurtTimerOnlyLetsStrongerHitsThrough() {
        HurtTimer timer = new HurtTimer();
        assertEquals(new HurtTimer.Result(5, true), timer.hit(1000, 5));
        assertEquals(HurtTimer.Result.NONE, timer.hit(1100, 5), "the same hit again is ignored");
        assertEquals(new HurtTimer.Result(2, false), timer.hit(1200, 7), "a stronger one only adds the difference");
        assertEquals(new HurtTimer.Result(4, true), timer.hit(1000 + HurtTimer.PROTECTED_MILLIS, 4));
    }

    @Test
    void combatTagsRunOut() {
        CombatTags tags = new CombatTags();
        UUID steve = UUID.randomUUID();
        assertTrue(tags.tag(steve, 0, 10_000));
        assertFalse(tags.tag(steve, 3_000, 10_000), "tagged again while in combat");
        assertEquals(10_000, tags.remainingMillis(steve, 3_000));
        assertEquals(1, tags.remainingSeconds(steve, 12_500));
        assertTrue(tags.expire(12_999).isEmpty());
        assertEquals(java.util.Set.of(steve), tags.expire(13_000));
        assertFalse(tags.tagged(steve, 13_000));
        assertTrue(tags.tag(steve, 20_000, 10_000), "a new fight");
        assertTrue(tags.clear(steve, 21_000));
        assertFalse(tags.clear(steve, 21_000));
    }
}
