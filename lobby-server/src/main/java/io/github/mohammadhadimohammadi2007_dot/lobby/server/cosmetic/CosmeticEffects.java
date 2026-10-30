package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.particle.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** True if a trail of this style draws something at {@code tick}. A feet trail only draws while walking. */
    static boolean due(Cosmetic.TrailStyle style, long tick, boolean moving) {
        return switch (style) {
            case FEET -> moving && tick % FEET_EVERY == 0;
            case HALO -> tick % HALO_EVERY == 0;
            case SPIRAL -> tick % SPIRAL_EVERY == 0;
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
        };
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
