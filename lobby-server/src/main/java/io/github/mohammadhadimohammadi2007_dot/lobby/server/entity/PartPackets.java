package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Builds the packets for {@link EntityPart}s. Built once per group of viewers, never per viewer. */
final class PartPackets {

    private static final int NO_DATA = 0;
    /** The name of the skin property in a game profile. */
    private static final String TEXTURES = "textures";

    private PartPackets() {
    }

    /** Spawn, metadata, equipment and size packets for one part. */
    static List<ServerPacket> spawn(int entityId, UUID uuid, EntityPart part) {
        List<ServerPacket> packets = new ArrayList<>(5);
        if (part.profile() != null) {
            packets.add(playerInfo(uuid, part.profile()));
        }
        packets.add(new SpawnEntityPacket(entityId, uuid, part.type(), part.position(),
                part.position().yaw(), NO_DATA, Vec.ZERO));
        if (!part.metadata().isEmpty()) {
            packets.add(new EntityMetaDataPacket(entityId, part.metadata()));
        }
        if (!part.equipment().isEmpty()) {
            packets.add(new EntityEquipmentPacket(entityId, part.equipment()));
        }
        if (part.scale() != 1) {
            packets.add(scale(entityId, part.scale()));
        }
        return packets;
    }

    /**
     * Who rides whom, sent after every part of the object exists on the client, since both ends of a
     * ride must already be spawned.
     */
    static List<ServerPacket> passengers(List<Integer> entityIds, List<EntityPart> parts) {
        List<ServerPacket> packets = new ArrayList<>(0);
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).passengers().isEmpty()) {
                packets.add(new SetPassengersPacket(entityIds.get(i),
                        parts.get(i).passengers().stream().map(entityIds::get).toList()));
            }
        }
        return packets;
    }

    /**
     * Only what changed between two versions of the same part, or {@code null} for nothing. The caller
     * respawns instead when the profile entry itself changed ({@link EntityPart.Profile#sameEntry}).
     */
    static List<ServerPacket> update(int entityId, UUID uuid, EntityPart before, EntityPart after) {
        if (before.equals(after)) {
            return null;
        }
        List<ServerPacket> packets = new ArrayList<>(3);
        if (!before.position().equals(after.position())) {
            packets.add(new EntityTeleportPacket(entityId, after.position(), Vec.ZERO, NO_DATA, false));
        }
        if (!before.metadata().equals(after.metadata())) {
            packets.add(new EntityMetaDataPacket(entityId, changed(before, after)));
        }
        if (!before.equipment().equals(after.equipment())) {
            packets.add(new EntityEquipmentPacket(entityId, after.equipment()));
        }
        if (before.scale() != after.scale()) {
            packets.add(scale(entityId, after.scale()));
        }
        if (after.profile() != null && before.profile() != null
                && !Objects.equals(before.profile().tabName(), after.profile().tabName())) {
            packets.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    entry(uuid, after.profile())));
        }
        return packets.isEmpty() ? null : packets;
    }

    /**
     * The player-list entry a client needs before it can draw a player-shaped entity. Hidden entries
     * never show in the tab list; temporary ones are removed again by {@link TabRemovals}.
     */
    private static ServerPacket playerInfo(UUID uuid, EntityPart.Profile profile) {
        EnumSet<PlayerInfoUpdatePacket.Action> actions = EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED);
        if (profile.tabName() != null) {
            actions.add(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        }
        return new PlayerInfoUpdatePacket(actions, entry(uuid, profile));
    }

    private static PlayerInfoUpdatePacket.Entry entry(UUID uuid, EntityPart.Profile profile) {
        List<PlayerInfoUpdatePacket.Property> properties = profile.skin() == null ? List.of()
                : List.of(new PlayerInfoUpdatePacket.Property(TEXTURES, profile.skin().textures(),
                        profile.skin().signature()));
        boolean listed = profile.listing() != EntityPart.Listing.HIDDEN;
        return new PlayerInfoUpdatePacket.Entry(uuid, profile.name(), properties, listed, 0, GameMode.SURVIVAL,
                profile.tabName(), null, 0, true);
    }

    private static ServerPacket scale(int entityId, double value) {
        return new EntityAttributesPacket(entityId,
                List.of(new EntityAttributesPacket.Property(Attribute.SCALE, value, List.of())));
    }

    static ServerPacket destroy(List<Integer> entityIds) {
        return new DestroyEntitiesPacket(entityIds);
    }

    /**
     * Removes player-list entries. Sent with every destroy of a player part: a client ignores a second
     * entry for a UUID it still has, so a changed skin would otherwise never show.
     */
    static ServerPacket removeEntries(List<UUID> uuids) {
        return new PlayerInfoRemovePacket(uuids);
    }

    /** Only the metadata entries that differ. */
    private static Map<Integer, Metadata.Entry<?>> changed(EntityPart before, EntityPart after) {
        Map<Integer, Metadata.Entry<?>> result = new HashMap<>();
        after.metadata().forEach((index, entry) -> {
            if (!entry.equals(before.metadata().get(index))) {
                result.put(index, entry);
            }
        });
        return result;
    }
}
