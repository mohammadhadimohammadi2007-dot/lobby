package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Metadata;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the packets for {@link EntityPart}s. Built once per group of viewers, never per viewer. */
final class PartPackets {

    private static final int NO_DATA = 0;

    private PartPackets() {
    }

    /** Spawn, metadata and equipment packets for one part. */
    static List<ServerPacket> spawn(int entityId, UUID uuid, EntityPart part) {
        List<ServerPacket> packets = new ArrayList<>(3);
        packets.add(new SpawnEntityPacket(entityId, uuid, part.type(), part.position(),
                part.position().yaw(), NO_DATA, Vec.ZERO));
        if (!part.metadata().isEmpty()) {
            packets.add(new EntityMetaDataPacket(entityId, part.metadata()));
        }
        if (!part.equipment().isEmpty()) {
            packets.add(new EntityEquipmentPacket(entityId, part.equipment()));
        }
        return packets;
    }

    /** Only what changed between two versions of the same part, or {@code null} for nothing. */
    static List<ServerPacket> update(int entityId, EntityPart before, EntityPart after) {
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
        return packets.isEmpty() ? null : packets;
    }

    static ServerPacket destroy(List<Integer> entityIds) {
        return new DestroyEntitiesPacket(entityIds);
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
