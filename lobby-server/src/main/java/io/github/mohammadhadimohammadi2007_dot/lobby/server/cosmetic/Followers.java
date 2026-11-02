package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.AttachEntityPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pets and balloons: entities that exist only in packets and follow their owner. The owner's own movement drives
 * them; no server entity, no AI and no tick-thread work. Effects thread only, like the trails.
 *
 * <p>A pet walks behind its owner on the ground (the client animates its legs from the movement). A balloon is an
 * invisible armor stand wearing the balloon on its head, floating above and lagging a little behind the owner; with
 * a lead, an invisible animal under it is tied to the owner (only animals can be tied, and the lead is drawn even when
 * the animal is not).
 *
 * <p>Viewers get the entities when they come near and lose them when they go away, hide other players' cosmetics or
 * move to another lobby instance. Movement is sent every {@link #MOVE_EVERY} ticks as absolute positions, so a move
 * a {@link ParticleBudget} drops is simply caught up by the next one.
 */
final class Followers {

    /** Ticks between two movement packets. */
    static final int MOVE_EVERY = 2;
    /** Ticks between two checks of who should see a follower (staggered per owner). */
    static final int VIEWERS_EVERY = 10;
    /** Movement packets one player receives per tick, and the whole server sends per tick, at most. */
    static final int MOVES_PER_VIEWER = 40;
    static final int MOVES_PER_TICK = 4000;
    /** Further than this from where it should be (a teleport, a portal), a follower jumps there. */
    private static final double JUMP_DISTANCE = 10;
    private static final double PET_BEHIND = 1.4;
    private static final double PET_SIDE = 0.8;
    /** A pet closer than this to its place stands still. */
    private static final double PET_CLOSE = 0.35;
    private static final double PET_MAX_STEP = 0.7;
    private static final double BALLOON_HEIGHT = 2.7;
    private static final double BALLOON_SIDE = 0.6;
    private static final double BALLOON_BEHIND = 0.3;
    /** How much of the way to its place a balloon floats each tick. */
    private static final double BALLOON_EASE = 0.25;
    /** From an armor stand's feet up to the middle of what it wears on its head. */
    private static final double STAND_HEAD = 1.75;
    /** The invisible animal holding the lead hangs this far under the balloon. */
    private static final double ANCHOR_BELOW = 0.6;
    private static final byte INVISIBLE = 0x20;
    private static final byte MARKER_STAND = 0x10;
    private static final double MOVED_SQUARED = 0.0004;

    /** Who sees which follower, decided by the service. */
    interface Hooks {
        /** Everyone who should see the owner's cosmetics at {@code at}, the owner included. */
        List<Player> viewers(Player owner, Pos at, @Nullable Instance instance);

        /** A pet's name tag rendered for its owner (MiniMessage and placeholders). */
        Component nameTag(String text, Player owner);
    }

    /** One pet or balloon of one owner. */
    private static final class Follower {
        final Cosmetic cosmetic;
        final boolean preview;
        /** Pet: the animal. Balloon: the armor stand, then the lead's animal if there is a lead. */
        final int[] ids;
        @Nullable Instance instance;
        Pos position;
        Pos sent;
        final Set<Player> viewers = new HashSet<>();
        @Nullable Component nameTag;

        Follower(Cosmetic cosmetic, boolean preview, int parts, Pos position, @Nullable Instance instance) {
            this.cosmetic = cosmetic;
            this.preview = preview;
            this.ids = new int[parts];
            for (int i = 0; i < parts; i++) {
                ids[i] = Entity.generateId();
            }
            this.position = position;
            this.sent = position;
            this.instance = instance;
        }
    }

    private final Hooks hooks;
    private final Map<UUID, Follower> pets = new HashMap<>();
    private final Map<UUID, Follower> balloons = new HashMap<>();
    private final Map<UUID, Instance> instances = new HashMap<>();
    private final ParticleBudget<Player> moves = new ParticleBudget<>();

    Followers(Hooks hooks) {
        this.hooks = hooks;
    }

    /** Starts a tick: new movement limits, and viewers who moved to another instance lose what they saw. */
    void startTick(List<Player> online) {
        moves.reset(MOVES_PER_VIEWER, MOVES_PER_TICK);
        for (Player player : online) {
            Instance now = player.getInstance();
            Instance before = now == null ? instances.remove(player.getUuid()) : instances.put(player.getUuid(), now);
            if (before != null && before != now) {
                forViewer(player);
            }
        }
    }

    /**
     * Shows, moves or removes one owner's pet or balloon. {@code wanted} is what they should have now ({@code null}
     * for nothing); {@code preview} if only they see it.
     */
    void update(Player owner, CosmeticType type, @Nullable Cosmetic wanted, boolean preview, long tick) {
        Map<UUID, Follower> all = type == CosmeticType.PET ? pets : balloons;
        Follower follower = all.get(owner.getUuid());
        if (follower != null && (wanted == null || follower.cosmetic != wanted || follower.preview != preview)) {
            remove(follower);
            all.remove(owner.getUuid());
            follower = null;
        }
        if (wanted == null) {
            return;
        }
        Pos at = owner.getPosition();
        Instance instance = owner.getInstance();
        boolean fresh = follower == null;
        if (fresh) {
            int parts = wanted.balloon() != null && wanted.balloon().leash() ? 2 : 1;
            follower = new Follower(wanted, preview, parts, place(wanted, at, null, instance, tick), instance);
            if (wanted.pet() != null && !wanted.pet().nameTag().isBlank()) {
                follower.nameTag = hooks.nameTag(wanted.pet().nameTag(), owner);
            }
            all.put(owner.getUuid(), follower);
        } else if (follower.instance != instance) {
            // The owner went to another lobby instance: start over there.
            remove(follower);
            follower.instance = instance;
            follower.position = place(wanted, at, null, instance, tick);
        } else {
            follower.position = place(wanted, at, follower.position, instance, tick);
        }
        if (fresh || Math.floorMod(tick + owner.getEntityId(), VIEWERS_EVERY) == 0 || follower.viewers.isEmpty()) {
            List<Player> wantedViewers = preview ? List.of(owner) : hooks.viewers(owner, at, instance);
            refreshViewers(follower, owner, wantedViewers);
        }
        boolean changed = follower.position.distanceSquared(follower.sent) > MOVED_SQUARED
                || Math.abs(follower.position.yaw() - follower.sent.yaw()) > 2;
        if (tick % MOVE_EVERY == 0 && changed) {
            List<ServerPacket> packets = movePackets(follower);
            boolean anySent = false;
            for (Player viewer : follower.viewers) {
                if (moves.take(viewer)) {
                    packets.forEach(viewer::sendPacket);
                    anySent = true;
                }
            }
            if (anySent || follower.viewers.isEmpty()) {
                follower.sent = follower.position;
            }
        }
    }

    /** Where a follower should be now, eased from where it was. */
    private static Pos place(Cosmetic cosmetic, Pos owner, @Nullable Pos current, @Nullable Instance instance,
                             long tick) {
        double yaw = Math.toRadians(owner.yaw());
        // The owner faces (-sin yaw, cos yaw); their right-hand side is (-cos yaw, -sin yaw).
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        if (cosmetic.pet() != null) {
            double targetX = owner.x() - forwardX * PET_BEHIND + rightX * PET_SIDE;
            double targetZ = owner.z() - forwardZ * PET_BEHIND + rightZ * PET_SIDE;
            if (current == null || current.distance(owner) > JUMP_DISTANCE) {
                return new Pos(targetX, ground(instance, targetX, owner.y(), targetZ), targetZ, owner.yaw(), 0);
            }
            double dx = targetX - current.x();
            double dz = targetZ - current.z();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < PET_CLOSE) {
                // Standing: look at the owner.
                return current.withView(lookYaw(owner.x() - current.x(), owner.z() - current.z()), 0);
            }
            double step = Math.min(distance, Math.min(PET_MAX_STEP, Math.max(0.2, distance * 0.3)));
            double x = current.x() + dx / distance * step;
            double z = current.z() + dz / distance * step;
            return new Pos(x, ground(instance, x, owner.y(), z), z, lookYaw(dx, dz), 0);
        }
        double bob = Math.sin(tick * 0.08) * 0.15;
        Pos target = new Pos(owner.x() + rightX * BALLOON_SIDE - forwardX * BALLOON_BEHIND,
                owner.y() + BALLOON_HEIGHT + bob, owner.z() + rightZ * BALLOON_SIDE - forwardZ * BALLOON_BEHIND);
        if (current == null || current.distance(target) > JUMP_DISTANCE) {
            return target;
        }
        return current.add(target.sub(current).mul(BALLOON_EASE)).withView(0, 0);
    }

    /** The yaw of something facing along (dx, dz). */
    private static float lookYaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * The floor height under (x, z), near {@code y} (the owner's feet), so a pet walks up and down steps. The lobby
     * map never changes, so reading its blocks off the tick thread is safe.
     */
    static double ground(@Nullable Block.Getter world, double x, double y, double z) {
        if (world == null) {
            return y;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(y) + 1;
        try {
            for (int by = top; by >= top - 4; by--) {
                if (world.getBlock(bx, by - 1, bz, Block.Getter.Condition.TYPE).solid()
                        && !world.getBlock(bx, by, bz, Block.Getter.Condition.TYPE).solid()) {
                    return by;
                }
            }
        } catch (RuntimeException unloaded) {
            return y;
        }
        return y;
    }

    private void refreshViewers(Follower follower, Player owner, List<Player> wanted) {
        Set<Player> keep = new HashSet<>(wanted);
        for (Iterator<Player> it = follower.viewers.iterator(); it.hasNext(); ) {
            Player viewer = it.next();
            if (!viewer.isOnline()) {
                it.remove();
            } else if (!keep.contains(viewer)) {
                viewer.sendPacket(destroy(follower));
                it.remove();
            }
        }
        List<ServerPacket> spawn = null;
        for (Player viewer : wanted) {
            if (viewer.isOnline() && follower.viewers.add(viewer)) {
                if (spawn == null) {
                    spawn = spawnPackets(follower, owner);
                }
                spawn.forEach(viewer::sendPacket);
            }
        }
    }

    private List<ServerPacket> spawnPackets(Follower follower, Player owner) {
        List<ServerPacket> packets = new ArrayList<>(6);
        Pos at = follower.position;
        if (follower.cosmetic.pet() instanceof Cosmetic.Pet pet) {
            int id = follower.ids[0];
            packets.add(new SpawnEntityPacket(id, UUID.randomUUID(), pet.entity(), at, at.yaw(), 0, Vec.ZERO));
            Map<Integer, Metadata.Entry<?>> metadata = new HashMap<>();
            metadata.put(MetadataDef.IS_SILENT.index(), Metadata.Boolean(true));
            metadata.put(MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true));
            if (pet.baby()) {
                metadata.put(MetadataDef.AgeableMob.IS_BABY.index(), Metadata.Boolean(true));
            }
            if (follower.nameTag != null) {
                metadata.put(MetadataDef.CUSTOM_NAME.index(), Metadata.OptComponent(follower.nameTag));
                metadata.put(MetadataDef.CUSTOM_NAME_VISIBLE.index(), Metadata.Boolean(true));
            }
            packets.add(new EntityMetaDataPacket(id, metadata));
            packets.add(new EntityHeadLookPacket(id, at.yaw()));
            return packets;
        }
        Cosmetic.Balloon balloon = follower.cosmetic.balloon();
        int stand = follower.ids[0];
        packets.add(new SpawnEntityPacket(stand, UUID.randomUUID(), EntityType.ARMOR_STAND, standAt(at), 0, 0,
                Vec.ZERO));
        packets.add(new EntityMetaDataPacket(stand, Map.of(
                MetadataDef.ENTITY_FLAGS.index(), Metadata.Byte(INVISIBLE),
                MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true),
                MetadataDef.ArmorStand.ARMOR_STAND_FLAGS.index(), Metadata.Byte(MARKER_STAND))));
        packets.add(new EntityEquipmentPacket(stand, Map.of(EquipmentSlot.HELMET, balloon.look().item())));
        if (follower.ids.length > 1) {
            int anchor = follower.ids[1];
            packets.add(new SpawnEntityPacket(anchor, UUID.randomUUID(), EntityType.CHICKEN, anchorAt(at), 0, 0,
                    Vec.ZERO));
            packets.add(new EntityMetaDataPacket(anchor, Map.of(
                    MetadataDef.ENTITY_FLAGS.index(), Metadata.Byte(INVISIBLE),
                    MetadataDef.IS_SILENT.index(), Metadata.Boolean(true),
                    MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true))));
            packets.add(new AttachEntityPacket(anchor, owner.getEntityId()));
        }
        return packets;
    }

    private static List<ServerPacket> movePackets(Follower follower) {
        Pos at = follower.position;
        if (follower.cosmetic.pet() != null) {
            int id = follower.ids[0];
            return List.of(new EntityTeleportPacket(id, at, Vec.ZERO, 0, true), new EntityHeadLookPacket(id, at.yaw()));
        }
        List<ServerPacket> packets = new ArrayList<>(2);
        packets.add(new EntityTeleportPacket(follower.ids[0], standAt(at), Vec.ZERO, 0, false));
        if (follower.ids.length > 1) {
            packets.add(new EntityTeleportPacket(follower.ids[1], anchorAt(at), Vec.ZERO, 0, false));
        }
        return packets;
    }

    private static ServerPacket destroy(Follower follower) {
        List<Integer> ids = new ArrayList<>(follower.ids.length);
        for (int id : follower.ids) {
            ids.add(id);
        }
        return new DestroyEntitiesPacket(ids);
    }

    private static Pos standAt(Pos balloon) {
        return balloon.sub(0, STAND_HEAD, 0).withView(0, 0);
    }

    private static Pos anchorAt(Pos balloon) {
        return balloon.sub(0, ANCHOR_BELOW, 0).withView(0, 0);
    }

    private static void remove(Follower follower) {
        if (follower.viewers.isEmpty()) {
            return;
        }
        ServerPacket packet = destroy(follower);
        for (Player viewer : follower.viewers) {
            if (viewer.isOnline()) {
                viewer.sendPacket(packet);
            }
        }
        follower.viewers.clear();
    }

    /** The viewer's client no longer has anything (another instance): send removals and forget what they saw. */
    private void forViewer(Player viewer) {
        for (Map<UUID, Follower> all : List.of(pets, balloons)) {
            for (Follower follower : all.values()) {
                if (follower.viewers.remove(viewer)) {
                    viewer.sendPacket(destroy(follower));
                }
            }
        }
    }

    /** A player left: their followers go, and they stop being a viewer. */
    void forget(UUID player) {
        instances.remove(player);
        for (Map<UUID, Follower> all : List.of(pets, balloons)) {
            Follower own = all.remove(player);
            if (own != null) {
                remove(own);
            }
            for (Follower follower : all.values()) {
                follower.viewers.removeIf(viewer -> viewer.getUuid().equals(player));
            }
        }
    }

    /** Removes every follower (cosmetics turned off or reloaded). */
    void clear() {
        for (Map<UUID, Follower> all : List.of(pets, balloons)) {
            all.values().forEach(Followers::remove);
            all.clear();
        }
    }

    /** How many pets and balloons are out, for tests and metrics. */
    int count() {
        return pets.size() + balloons.size();
    }

    /** The entity ids of a player's pet or balloon, empty if none, for tests. */
    List<Integer> idsOf(UUID owner, CosmeticType type) {
        Follower follower = (type == CosmeticType.PET ? pets : balloons).get(owner);
        if (follower == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (int id : follower.ids) {
            ids.add(id);
        }
        return ids;
    }

    /** Movement packets dropped by the limits since the start. */
    long droppedMoves() {
        return moves.dropped();
    }
}
