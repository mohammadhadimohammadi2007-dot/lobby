package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObject;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.EntityPart;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.HologramText;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One NPC, shown to the players near it with packets only: the body (a player with a skin, or any other
 * entity) and its name tag hologram above it.
 *
 * <p>Body and name are one object, so a click on either is a click on the NPC, and both share one render
 * per group of viewers. Turning towards players is done per viewer with two small rotation packets and
 * never re-renders anything.
 */
public final class Npc implements ClientObject {

    /** NPC objects are named {@code npc:<name>} in the renderer, so they never clash with holograms. */
    public static final String OBJECT_PREFIX = "npc:";
    /** Entity flag 0x40: glowing outline. */
    private static final byte GLOWING = 0x40;
    /** Every outer skin layer: hat, jacket, sleeves, trouser legs and cape. */
    private static final byte ALL_SKIN_LAYERS = 0x7F;
    /** Rotations closer than this, in degrees, are not sent again. */
    private static final float TURN_STEP_DEGREES = 2f;
    private static final int PACK_SHIFT = 32;
    private static final long PACK_MASK = 0xFFFFFFFFL;

    private final NpcData data;
    private final Hologram.Services services;
    /** The rotation each viewer last got, packed into a long, so unchanged turns are not sent again. */
    private final Map<UUID, Long> lastLook = new ConcurrentHashMap<>();

    public Npc(NpcData data, Hologram.Services services) {
        this.data = data;
        this.services = services;
    }

    /** The stored NPC behind this one. */
    public NpcData data() {
        return data;
    }

    @Override
    public String name() {
        return OBJECT_PREFIX + data.name();
    }

    @Override
    public Pos position() {
        return data.position();
    }

    @Override
    public WorldScope scope() {
        return services.scope();
    }

    @Override
    public double viewDistance() {
        return data.viewDistance();
    }

    @Override
    public boolean visibleTo(Player viewer) {
        String permission = data.permission();
        return permission.isEmpty() || services.permissions().hasPermission(viewer, permission);
    }

    @Override
    public Object variantKey(Player viewer) {
        boolean mirror = data.skin().kind() == NpcSkin.Kind.MIRROR;
        return HologramText.variant(data.nameTag(), services.text(), viewer, services.legacyClient().test(viewer),
                mirror);
    }

    @Override
    public int updateIntervalTicks() {
        return data.nameTag().effectiveUpdateIntervalTicks();
    }

    /** The body first, then the name tag: the body's entity id is always the first one. */
    @Override
    public List<EntityPart> render(Object key, Player viewer) {
        boolean legacy = key instanceof HologramText.Variant variant && variant.legacy();
        List<EntityPart> parts = new ArrayList<>();
        parts.add(body(viewer));
        if (!data.nameTag().lines().isEmpty()) {
            parts.addAll(HologramText.parts(data.nameTag(), services.text(), viewer, legacy));
        }
        return parts;
    }

    private EntityPart body(Player viewer) {
        Map<Integer, Metadata.Entry<?>> metadata = new HashMap<>();
        if (data.glowing()) {
            metadata.put(MetadataDef.ENTITY_FLAGS.index(), Metadata.Byte(GLOWING));
        }
        metadata.put(MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true));
        metadata.put(MetadataDef.IS_SILENT.index(), Metadata.Boolean(true));
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, Material> item : data.equipment().entrySet()) {
            equipment.put(item.getKey(), ItemStack.of(item.getValue()));
        }
        if (!data.isPlayer()) {
            return new EntityPart(data.type(), data.position(), metadata, equipment);
        }
        metadata.put(MetadataDef.Avatar.DISPLAYED_MODEL_PARTS_FLAGS.index(), Metadata.Byte(ALL_SKIN_LAYERS));
        PlayerSkin skin = data.skin().kind() == NpcSkin.Kind.MIRROR ? viewer.getSkin() : data.resolvedSkin();
        return new EntityPart(EntityType.PLAYER, data.position(), metadata, equipment,
                new EntityPart.Profile(data.profileName(), skin));
    }

    @Override
    public boolean hasViewerPackets() {
        return data.turnToPlayer();
    }

    /**
     * Turns the NPC towards this viewer when they are near enough, and back to how it was placed when
     * they walk away. Sent only when the direction changed by more than {@link #TURN_STEP_DEGREES}.
     */
    @Override
    public List<ServerPacket> viewerPackets(Player viewer, List<Integer> entityIds, boolean justSpawned) {
        if (entityIds.isEmpty()) {
            return List.of();
        }
        Pos npc = data.position();
        Pos target = viewer.getPosition();
        float yaw = npc.yaw();
        float pitch = npc.pitch();
        if (npc.distanceSquared(target) <= data.turnDistance() * data.turnDistance()) {
            double dx = target.x() - npc.x();
            double dz = target.z() - npc.z();
            double dy = (target.y() + viewer.getEyeHeight()) - (npc.y() + data.type().eyeHeight());
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        long packed = pack(round(yaw), round(pitch));
        Long previous = lastLook.put(viewer.getUuid(), packed);
        if (!justSpawned && previous != null && previous == packed) {
            return List.of();
        }
        int body = entityIds.getFirst();
        return List.of(new EntityRotationPacket(body, yaw, pitch, true), new EntityHeadLookPacket(body, yaw));
    }

    @Override
    public void viewerGone(Player viewer) {
        lastLook.remove(viewer.getUuid());
    }

    private static float round(float degrees) {
        return Math.round(degrees / TURN_STEP_DEGREES) * TURN_STEP_DEGREES;
    }

    private static long pack(float yaw, float pitch) {
        return ((long) Float.floatToIntBits(yaw) << PACK_SHIFT) | (Float.floatToIntBits(pitch) & PACK_MASK);
    }
}
