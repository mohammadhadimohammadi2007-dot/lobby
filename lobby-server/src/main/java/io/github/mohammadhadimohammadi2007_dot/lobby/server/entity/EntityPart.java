package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
 * @param profile   for a {@code PLAYER} part: the name and skin the client must know before it can spawn
 *                  it; {@code null} for every other part
 */
public record EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                         Map<EquipmentSlot, ItemStack> equipment, @Nullable Profile profile) {

    /**
     * The player profile of a player-shaped part. A client only draws a player it has in its player
     * list, with the skin from that list.
     *
     * @param name at most 16 characters; hidden above the head by the lobby's hidden-name team
     * @param skin the skin, or {@code null} for Minecraft's default one
     */
    public record Profile(String name, @Nullable PlayerSkin skin) {
    }

    public EntityPart {
        metadata = Map.copyOf(metadata);
        equipment = Map.copyOf(equipment);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata) {
        this(type, position, metadata, Map.of(), null);
    }

    public EntityPart(EntityType type, Pos position, Map<Integer, Metadata.Entry<?>> metadata,
                      Map<EquipmentSlot, ItemStack> equipment) {
        this(type, position, metadata, equipment, null);
    }

    /** The same part at another position. */
    public EntityPart at(Pos newPosition) {
        return new EntityPart(type, newPosition, metadata, equipment, profile);
    }

    /** The same part moved by {@code offset}. */
    public EntityPart moved(Vec offset) {
        return at(position.add(offset));
    }
}
