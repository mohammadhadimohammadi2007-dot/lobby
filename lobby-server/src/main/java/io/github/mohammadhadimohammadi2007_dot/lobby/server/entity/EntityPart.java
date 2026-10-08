package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.item.ItemStack;

import java.util.Map;

/**
 * One entity as the client should see it: everything needed to spawn it and nothing else. It only exists
 * in packets, never in the world, so it costs no server tick time and cannot be hit or pushed.
 *
 * <p>Two parts that are equal produce the very same packets, which is how the renderer groups viewers who
 * can share one entity.
 *
 * @param metadata  entity metadata by index, from {@link Metadata}
 * @param equipment held and worn items, empty for most parts
 */
public record EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                         Map<EquipmentSlot, ItemStack> equipment) {

    public EntityPart {
        metadata = Map.copyOf(metadata);
        equipment = Map.copyOf(equipment);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata) {
        this(type, position, metadata, Map.of());
    }

    /** The same part at another position. */
    public EntityPart at(Pos newPosition) {
        return new EntityPart(type, newPosition, metadata, equipment);
    }

    /** The same part moved by {@code offset}. */
    public EntityPart moved(Vec offset) {
        return at(position.add(offset));
    }
}
