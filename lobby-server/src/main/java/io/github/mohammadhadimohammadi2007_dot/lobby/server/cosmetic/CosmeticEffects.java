package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.kyori.adventure.util.RGBLike;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.particle.Particle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Where the particles of trails and gadgets go. Only geometry and packets, no state, so it is the same on every
 * thread; the lobby sends these from the {@code lobby-effects} thread.
 */
final class CosmeticEffects {

    /** A feet trail puffs every this many ticks while walking. */
    static final int FEET_EVERY = 2;
    /** A halo is drawn every this many ticks. */
    static final int HALO_EVERY = 5;
    static final int HALO_POINTS = 6;
    static final double HALO_RADIUS = 0.35;
    static final double HALO_HEIGHT = 2.15;
    /** A spiral moves one step every this many ticks. */
    static final int SPIRAL_EVERY = 2;
    static final double SPIRAL_RADIUS = 0.6;
    /** Ticks a spiral takes from the feet to the head. */
    static final int SPIRAL_CYCLE = 40;
    static final int BURST_POINTS = 16;
    static final int LEAP_PUFF = 12;
    /** Ticks from a firework's launch to its explosion. */
    static final int FIREWORK_FLIGHT_TICKS = 22;
    /** Entity status that makes a firework rocket explode on the client. */
    private static final byte FIREWORK_EXPLODE = 17;
    /** How fast a firework leaves the player, blocks per second. */
    private static final double FIREWORK_SPEED = 2;

    private CosmeticEffects() {
    }

    /** A ring is drawn every this many ticks. */
    static final int RING_EVERY = 4;
    static final int RING_POINTS = 8;
    static final double RING_RADIUS = 0.75;
    /** Half of the wings (one wing) is drawn every this many ticks, so both are drawn every twice as many. */
    static final int WINGS_EVERY = 2;
    /** One wing: (out from the spine, up from the feet) of each point, in blocks. */
    private static final double[][] WING = {
            {0.25, 1.55}, {0.45, 1.7}, {0.65, 1.82}, {0.85, 1.88}, {1.0, 1.85},
            {0.45, 1.45}, {0.7, 1.5}, {0.9, 1.55}, {0.6, 1.25}};
    /** How far behind the back the wings are. */
    private static final double WINGS_BACK = 0.3;
    /** A cloud puffs every this many ticks. */
    static final int CLOUD_EVERY = 4;
    static final double CLOUD_HEIGHT = 2.7;
    /** Confetti pieces per point of power. */
    static final int CONFETTI_PIECES = 16;
    /** Particles per block of a line (a grapple's rope, a paintball's flight), and the most in one line. */
    private static final double LINE_STEP = 0.75;
    private static final int LINE_MAX = 48;
    /** Steps per block when looking for the block a player aims at. */
    private static final double RAY_STEP = 0.2;
    /** The most blocks one paintball paints. */
    static final int PAINT_MAX = 64;

    /** True if a trail of this style draws something at {@code tick}. A feet trail only draws while walking. */
    static boolean due(Cosmetic.TrailStyle style, long tick, boolean moving) {
        return switch (style) {
            case FEET -> moving && tick % FEET_EVERY == 0;
            case HALO -> tick % HALO_EVERY == 0;
            case SPIRAL -> tick % SPIRAL_EVERY == 0;
            case RING -> tick % RING_EVERY == 0;
            case WINGS -> tick % WINGS_EVERY == 0;
            case CLOUD -> tick % CLOUD_EVERY == 0;
        };
    }

    /** The packets of one step of a trail for a player standing at {@code at}. */
    static List<ParticlePacket> trail(Cosmetic.Trail trail, Pos at, long tick) {
        float spread = (float) trail.spread();
        float speed = (float) trail.speed();
        return switch (trail.style()) {
            case FEET -> List.of(new ParticlePacket(trail.particle(), at.x(), at.y() + 0.1, at.z(), spread, 0.05f, spread,
                    speed, trail.amount()));
            case HALO -> {
                List<ParticlePacket> points = new ArrayList<>(HALO_POINTS);
                double turn = tick * 0.15;
                for (int i = 0; i < HALO_POINTS; i++) {
                    double angle = turn + i * (Math.PI * 2 / HALO_POINTS);
                    points.add(point(trail.particle(), at.x() + Math.cos(angle) * HALO_RADIUS, at.y() + HALO_HEIGHT,
                            at.z() + Math.sin(angle) * HALO_RADIUS, speed));
                }
                yield points;
            }
            case SPIRAL -> {
                double height = (tick % SPIRAL_CYCLE) * 2.0 / SPIRAL_CYCLE;
                double angle = tick * 0.4;
                yield List.of(
                        point(trail.particle(), at.x() + Math.cos(angle) * SPIRAL_RADIUS, at.y() + height,
                                at.z() + Math.sin(angle) * SPIRAL_RADIUS, speed),
                        point(trail.particle(), at.x() - Math.cos(angle) * SPIRAL_RADIUS, at.y() + height,
                                at.z() - Math.sin(angle) * SPIRAL_RADIUS, speed));
            }
            case RING -> {
                List<ParticlePacket> points = new ArrayList<>(RING_POINTS);
                double turn = tick * 0.1;
                for (int i = 0; i < RING_POINTS; i++) {
                    double angle = turn + i * (Math.PI * 2 / RING_POINTS);
                    points.add(point(trail.particle(), at.x() + Math.cos(angle) * RING_RADIUS, at.y() + 0.1,
                            at.z() + Math.sin(angle) * RING_RADIUS, speed));
                }
                yield points;
            }
            case WINGS -> wing(trail, at, tick);
            case CLOUD -> {
                ParticlePacket cloud = new ParticlePacket(trail.particle(), at.x(), at.y() + CLOUD_HEIGHT, at.z(),
                        0.3f, 0.05f, 0.3f, speed, trail.amount());
                yield trail.drip() == null ? List.of(cloud) : List.of(cloud, new ParticlePacket(trail.drip(), at.x(),
                        at.y() + CLOUD_HEIGHT - 0.2, at.z(), 0.25f, 0, 0.25f, 0, 1));
            }
        };
    }

    /**
     * One of the two wings (left on even steps, right on odd ones), behind the back and turned with the body, gently
     * flapping. Drawing a wing at a time keeps each step under the usual per-viewer limit.
     */
    private static List<ParticlePacket> wing(Cosmetic.Trail trail, Pos at, long tick) {
        double side = (tick / WINGS_EVERY) % 2 == 0 ? 1 : -1;
        double flap = 1 + 0.15 * Math.sin(tick * 0.25);
        double yaw = Math.toRadians(at.yaw());
        // Facing (-sin yaw, cos yaw): behind is the opposite, and the right-hand side is (-cos yaw, -sin yaw).
        double backX = Math.sin(yaw) * WINGS_BACK;
        double backZ = -Math.cos(yaw) * WINGS_BACK;
        double sideX = -Math.cos(yaw) * side;
        double sideZ = -Math.sin(yaw) * side;
        List<ParticlePacket> points = new ArrayList<>(WING.length);
        for (double[] point : WING) {
            double out = point[0] * flap;
            points.add(point(trail.particle(), at.x() + backX + sideX * out, at.y() + point[1],
                    at.z() + backZ + sideZ * out, (float) trail.speed()));
        }
        return points;
    }

    // ---------------- Gadgets that aim ----------------

    /**
     * The block a player aims at.
     *
     * @param block the block's position
     * @param point where the aim enters it
     */
    record Hit(Point block, Pos point) {
    }

    /**
     * The first solid block along {@code direction} from {@code eye}, at most {@code range} blocks away, or
     * {@code null}. Stops at the edge of what is loaded.
     */
    static @Nullable Hit aim(Block.Getter world, Pos eye, Vec direction, int range) {
        if (direction.isZero()) {
            return null;
        }
        Vec step = direction.normalize().mul(RAY_STEP);
        double x = eye.x();
        double y = eye.y();
        double z = eye.z();
        for (double travelled = 0; travelled <= range; travelled += RAY_STEP) {
            x += step.x();
            y += step.y();
            z += step.z();
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            Block block;
            try {
                block = world.getBlock(bx, by, bz, Block.Getter.Condition.TYPE);
            } catch (RuntimeException unloaded) {
                return null;
            }
            if (block != null && block.solid()) {
                return new Hit(new Vec(bx, by, bz), new Pos(x, y, z));
            }
        }
        return null;
    }

    /** Particles along a straight line, one every {@link #LINE_STEP} blocks (at most {@link #LINE_MAX}). */
    static List<ParticlePacket> line(Particle particle, Point from, Point to) {
        Vec path = to.sub(from).asVec();
        int count = (int) Math.min(LINE_MAX, Math.max(1, path.length() / LINE_STEP));
        List<ParticlePacket> points = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Point at = from.add(path.mul((double) i / count));
            points.add(point(particle, at.x(), at.y(), at.z(), 0));
        }
        return points;
    }

    /**
     * A paintball's splash: the paint for each block around {@code center} (within {@code radius}) and the packets
     * that put the real blocks back. Blocks with block entities (signs, heads, chests) are never painted, since
     * putting them back would lose what they show.
     */
    record Paint(List<ServerPacket> paint, List<ServerPacket> restore) {
    }

    static Paint paint(Block.Getter world, Point center, double radius, List<Block> colors, Random random) {
        List<ServerPacket> paint = new ArrayList<>();
        List<ServerPacket> restore = new ArrayList<>();
        int reach = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;
        for (int dx = -reach; dx <= reach && paint.size() < PAINT_MAX; dx++) {
            for (int dy = -reach; dy <= reach && paint.size() < PAINT_MAX; dy++) {
                for (int dz = -reach; dz <= reach && paint.size() < PAINT_MAX; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                        continue;
                    }
                    Point at = center.add(dx, dy, dz);
                    Block block;
                    try {
                        block = world.getBlock(at, Block.Getter.Condition.TYPE);
                    } catch (RuntimeException unloaded) {
                        continue;
                    }
                    if (block == null || !block.solid() || block.blockEntityType() != null) {
                        continue;
                    }
                    paint.add(new BlockChangePacket(at, colors.get(random.nextInt(colors.size()))));
                    restore.add(new BlockChangePacket(at, block));
                }
            }
        }
        return new Paint(paint, restore);
    }

    /**
     * Confetti shot from {@code eye} towards {@code direction}: each piece is one particle moving out (a particle
     * packet with count 0 uses its offset as the direction of motion).
     */
    static List<ParticlePacket> confetti(List<RGBLike> colors, Pos eye, Vec direction, double power, Random random) {
        int pieces = (int) Math.max(1, Math.round(CONFETTI_PIECES * power));
        Vec forward = direction.isZero() ? new Vec(0, 1, 0) : direction.normalize();
        List<ParticlePacket> packets = new ArrayList<>(pieces);
        for (int i = 0; i < pieces; i++) {
            Vec motion = forward.add(random.nextGaussian() * 0.25, random.nextGaussian() * 0.25 + 0.15,
                    random.nextGaussian() * 0.25);
            Particle dust = Particle.DUST.withColor(colors.get(random.nextInt(colors.size())));
            packets.add(new ParticlePacket(dust, eye.x(), eye.y(), eye.z(), (float) motion.x(), (float) motion.y(),
                    (float) motion.z(), 0.6f + random.nextFloat() * 0.6f, 0));
        }
        return packets;
    }

    /** A primed TNT that exists only for its viewers, blinking for {@code fuseTicks}: spawn and remove. */
    record Tnt(List<ServerPacket> spawn, List<ServerPacket> explode) {
    }

    static Tnt tnt(Pos at, int fuseTicks) {
        int id = Entity.generateId();
        Map<Integer, Metadata.Entry<?>> metadata = Map.of(
                MetadataDef.HAS_NO_GRAVITY.index(), Metadata.Boolean(true),
                MetadataDef.PrimedTnt.FUSE_TIME.index(), Metadata.VarInt(fuseTicks));
        List<ServerPacket> spawn = List.of(
                new SpawnEntityPacket(id, UUID.randomUUID(), EntityType.TNT, at.withView(0, 0), 0, 0, Vec.ZERO),
                new EntityMetaDataPacket(id, metadata));
        List<ServerPacket> explode = List.of(new DestroyEntitiesPacket(id),
                new ParticlePacket(Particle.EXPLOSION_EMITTER, at.x(), at.y() + 0.5, at.z(), 0, 0, 0, 0, 1));
        return new Tnt(spawn, explode);
    }

    /** A lightning bolt that exists only for its viewers: spawn, and remove once it has flashed. */
    record Lightning(List<ServerPacket> spawn, List<ServerPacket> remove) {
    }

    static Lightning lightning(Pos at) {
        int id = Entity.generateId();
        return new Lightning(
                List.of(new SpawnEntityPacket(id, UUID.randomUUID(), EntityType.LIGHTNING_BOLT, at.withView(0, 0), 0, 0,
                        Vec.ZERO)),
                List.of(new DestroyEntitiesPacket(id)));
    }

    /** A ring of particles around a player, {@code radius} blocks wide, at chest height. */
    static List<ParticlePacket> burst(Particle particle, Pos at, double radius) {
        List<ParticlePacket> points = new ArrayList<>(BURST_POINTS);
        for (int i = 0; i < BURST_POINTS; i++) {
            double angle = i * (Math.PI * 2 / BURST_POINTS);
            points.add(point(particle, at.x() + Math.cos(angle) * radius, at.y() + 1,
                    at.z() + Math.sin(angle) * radius, 0));
        }
        return points;
    }

    /** A puff at the feet of a player who leaps. */
    static ParticlePacket puff(Particle particle, Pos at) {
        return new ParticlePacket(particle, at.x(), at.y() + 0.2, at.z(), 0.3f, 0.1f, 0.3f, 0.02f, LEAP_PUFF);
    }

    /** One particle exactly at a point (count 1 and no spread, so it is not scattered). */
    private static ParticlePacket point(Particle particle, double x, double y, double z, float speed) {
        return new ParticlePacket(particle, x, y, z, 0, 0, 0, speed, 1);
    }

    /**
     * A firework that exists only for its viewers: launched with {@link #launch()}, exploded and removed with
     * {@link #explode()} {@link #FIREWORK_FLIGHT_TICKS} ticks later. The client flies and draws it.
     */
    record Firework(List<ServerPacket> launch, List<ServerPacket> explode) {
    }

    /** A firework rocket at {@code at} (above the player's head), with the gadget's colors and shape. */
    static Firework firework(Cosmetic.Gadget gadget, Pos at) {
        int id = Entity.generateId();
        ItemStack rocket = ItemStack.of(Material.FIREWORK_ROCKET).with(DataComponents.FIREWORKS,
                new FireworkList(1, List.of(new FireworkExplosion(gadget.shape(), gadget.colors(), List.of(), true, true))));
        Map<Integer, Metadata.Entry<?>> metadata = Map.of(MetadataDef.FireworkRocketEntity.ITEM.index(),
                Metadata.ItemStack(rocket));
        Vec velocity = new Vec(0, FIREWORK_SPEED, 0);
        List<ServerPacket> launch = List.of(
                new SpawnEntityPacket(id, UUID.randomUUID(), EntityType.FIREWORK_ROCKET, at.withView(0, -90), 0, 0, velocity),
                new EntityMetaDataPacket(id, metadata));
        List<ServerPacket> explode = List.of(new EntityStatusPacket(id, FIREWORK_EXPLODE), new DestroyEntitiesPacket(id));
        return new Firework(launch, explode);
    }
}
