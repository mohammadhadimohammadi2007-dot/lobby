package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import net.minestom.server.entity.EntityPose;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How a player NPC stands, with FancyNpcs' names for its {@code pose} attribute.
 *
 * <p>Sitting is not a pose a client draws on its own: like FancyNpcs, the NPC rides an invisible seat
 * entity at its position, and a riding player is drawn sitting.
 */
public enum NpcPose {
    STANDING(EntityPose.STANDING, 1.8),
    CROUCHING(EntityPose.SNEAKING, 1.5),
    SLEEPING(EntityPose.SLEEPING, 0.2),
    SWIMMING(EntityPose.SWIMMING, 0.6),
    SITTING(EntityPose.STANDING, 1.8 - NpcPose.SEAT_DROP);

    /**
     * How far below its seat a riding player's feet are: a player's vehicle attachment point in
     * vanilla Minecraft. Only used to put the name tag at the right height.
     */
    static final double SEAT_DROP = 0.6;

    private final EntityPose entityPose;
    private final double playerHeight;

    NpcPose(EntityPose entityPose, double playerHeight) {
        this.entityPose = entityPose;
        this.playerHeight = playerHeight;
    }

    /** The pose sent in the entity's metadata. */
    public EntityPose entityPose() {
        return entityPose;
    }

    /** How tall a normal-size player in this pose looks, from its position up, in blocks. */
    public double playerHeight() {
        return playerHeight;
    }

    /** What an admin types and the file stores: {@code crouching}. */
    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The pose with this name (FancyNpcs' names, plus {@code sneaking} for crouching), or {@code null}. */
    public static @Nullable NpcPose fromName(String name) {
        String wanted = name.strip().toUpperCase(Locale.ROOT);
        if (wanted.equals("SNEAKING")) {
            return CROUCHING;
        }
        for (NpcPose pose : values()) {
            if (pose.name().equals(wanted)) {
                return pose;
            }
        }
        return null;
    }
}
