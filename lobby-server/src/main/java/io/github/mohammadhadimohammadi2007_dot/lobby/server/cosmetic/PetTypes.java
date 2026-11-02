package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.MetadataHolder;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.entity.metadata.EntityMeta;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What kind of entity a pet is, from Minestom's own metadata classes: a client disconnects on metadata an entity type
 * does not have, so a baby flag must only go to animals that have babies.
 */
final class PetTypes {

    private static final Map<EntityType, EntityMeta> METAS = new ConcurrentHashMap<>();

    private PetTypes() {
    }

    /** True for animals and mobs (living entities other than players and armor stands). */
    static boolean living(EntityType type) {
        return type != EntityType.PLAYER && type != EntityType.ARMOR_STAND && meta(type) instanceof LivingEntityMeta;
    }

    /** True for animals that have babies. */
    static boolean ageable(EntityType type) {
        return meta(type) instanceof AgeableMobMeta;
    }

    private static @Nullable EntityMeta meta(EntityType type) {
        return METAS.computeIfAbsent(type, key -> MetadataHolder.createMeta(key, null, new MetadataHolder(entries -> { })));
    }
}
