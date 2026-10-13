package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;

/**
 * What the item in the attacker's hand does in a fight.
 *
 * @param damage      attack damage, the hand's 1 included (a modern diamond sword: 7)
 * @param attackSpeed swings per second at full charge (modern only; a sword: 1.6)
 * @param sharpness   level of Sharpness
 * @param knockback   level of Knockback
 * @param sweeping    level of Sweeping Edge
 * @param sword       true for swords, which can sweep
 */
public record Weapon(double damage, double attackSpeed, int sharpness, int knockback, int sweeping, boolean sword) {

    /** An empty hand. */
    public static final Weapon HAND = new Weapon(1, 4, 0, 0, 0, false);

    private static final double BASE_DAMAGE = 1;
    private static final double BASE_ATTACK_SPEED = 4;

    /** The weapon {@code item} is in {@code mode}. */
    public static Weapon of(ItemStack item, CombatMode mode) {
        if (item.isAir()) {
            return HAND;
        }
        AttributeList modifiers = item.get(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);
        double damage = BASE_DAMAGE + mainHand(modifiers, Attribute.ATTACK_DAMAGE);
        double speed = BASE_ATTACK_SPEED + mainHand(modifiers, Attribute.ATTACK_SPEED);
        if (mode == CombatMode.LEGACY) {
            Double legacy = legacyDamage(item.material());
            if (legacy != null) {
                damage = legacy;
            }
        }
        EnchantmentList enchantments = item.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY);
        return new Weapon(damage, Math.max(0.1, speed), enchantments.level(Enchantment.SHARPNESS),
                enchantments.level(Enchantment.KNOCKBACK), enchantments.level(Enchantment.SWEEPING_EDGE),
                item.material().key().value().endsWith("_sword"));
    }

    /** Sum of the item's "add" modifiers of an attribute that apply in the main hand. */
    static double mainHand(AttributeList modifiers, Attribute attribute) {
        double sum = 0;
        for (AttributeList.Modifier modifier : modifiers.modifiers()) {
            if (modifier.attribute().equals(attribute) && modifier.slot().contains(EquipmentSlot.MAIN_HAND)
                    && modifier.modifier().operation() == AttributeOperation.ADD_VALUE) {
                sum += modifier.modifier().amount();
            }
        }
        return sum;
    }

    /**
     * Damage of a tool in Minecraft 1.8, the hand's 1 included, or {@code null} for items without a 1.8 value
     * (they keep their modern damage). From 1.8's code: a sword deals 4 plus its material's bonus, an axe 3, a
     * pickaxe 2 and a shovel 1, where the bonus is 0 for wood and gold, 1 for stone, 2 for iron and 3 for
     * diamond. Netherite did not exist; it gets one more than diamond, as it has in modern versions.
     */
    static Double legacyDamage(Material material) {
        String name = material.key().value();
        int underscore = name.indexOf('_');
        if (underscore < 0) {
            return null;
        }
        Integer bonus = switch (name.substring(0, underscore)) {
            case "wooden", "golden" -> 0;
            case "stone" -> 1;
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> null;
        };
        Integer tool = switch (name.substring(underscore + 1)) {
            case "sword" -> 4;
            case "axe" -> 3;
            case "pickaxe" -> 2;
            case "shovel" -> 1;
            case "hoe" -> 0;
            default -> null;
        };
        if (bonus == null || tool == null) {
            return null;
        }
        // Hoes dealt no extra damage in 1.8, whatever their material.
        return BASE_DAMAGE + (tool == 0 ? 0 : tool + bonus);
    }
}
