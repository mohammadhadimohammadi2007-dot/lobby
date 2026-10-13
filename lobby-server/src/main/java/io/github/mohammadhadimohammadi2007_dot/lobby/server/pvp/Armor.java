package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;

import java.util.List;

/**
 * What the defender wears.
 *
 * @param points     armor points (a full diamond set: 20)
 * @param toughness  armor toughness (diamond: 2 per piece; only modern combat uses it)
 * @param protection sum of the Protection levels of all pieces
 */
public record Armor(double points, double toughness, int protection) {

    public static final Armor NONE = new Armor(0, 0, 0);

    private static final List<EquipmentSlot> PIECES =
            List.of(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS);

    /** The armor {@code entity} has on. The points are the same in 1.8 and modern for every 1.8 material. */
    public static Armor of(LivingEntity entity) {
        double points = 0;
        double toughness = 0;
        int protection = 0;
        for (EquipmentSlot slot : PIECES) {
            ItemStack item = entity.getEquipment(slot);
            if (item.isAir()) {
                continue;
            }
            AttributeList modifiers = item.get(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);
            for (AttributeList.Modifier modifier : modifiers.modifiers()) {
                if (!modifier.slot().contains(slot) || modifier.modifier().operation() != AttributeOperation.ADD_VALUE) {
                    continue;
                }
                if (modifier.attribute().equals(Attribute.ARMOR)) {
                    points += modifier.modifier().amount();
                } else if (modifier.attribute().equals(Attribute.ARMOR_TOUGHNESS)) {
                    toughness += modifier.modifier().amount();
                }
            }
            protection += item.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY).level(Enchantment.PROTECTION);
        }
        return new Armor(points, toughness, protection);
    }
}
