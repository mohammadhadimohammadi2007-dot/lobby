package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saved choices, the particle budget and where trails draw. No server needed. */
class CosmeticLogicTest {

    @Test
    void choicesAreSavedAsShortTextAndReadBack() {
        CosmeticChoices choices = CosmeticChoices.NONE.with(CosmeticType.TRAIL, "flame")
                .with(CosmeticType.HAT, "glass").withHideOthers(true);
        assertEquals("trail=flame,hat=glass,hide-others", choices.encode());
        assertEquals(choices, CosmeticChoices.decode(choices.encode()));
        assertNull(CosmeticChoices.NONE.encode(), "nothing chosen: nothing to save");

        CosmeticChoices without = choices.with(CosmeticType.TRAIL, null);
        assertNull(without.get(CosmeticType.TRAIL));
        assertEquals("glass", without.get(CosmeticType.HAT));
    }

    @Test
    void brokenSavedTextIsSkippedPieceByPiece() {
        CosmeticChoices read = CosmeticChoices.decode("trail=Flame, wings=big ,hat=bad id!,gadget=leap,,junk");
        assertEquals("flame", read.get(CosmeticType.TRAIL));
        assertNull(read.get(CosmeticType.HAT));
        assertEquals("leap", read.get(CosmeticType.GADGET));
        assertFalse(read.hideOthers());
        assertEquals(CosmeticChoices.NONE, CosmeticChoices.decode(null));
    }

    @Test
    void theBudgetLimitsEachViewerAndTheWholeTick() {
        ParticleBudget<String> budget = new ParticleBudget<>();
        budget.reset(2, 5);
        assertTrue(budget.take("a"));
        assertTrue(budget.take("a"));
        assertFalse(budget.take("a"), "a got its 2 for this tick");
        assertTrue(budget.take("b"));
        assertTrue(budget.take("b"));
        assertTrue(budget.take("c"));
        assertTrue(budget.exhausted());
        assertFalse(budget.take("d"), "5 sent in this tick");
        assertEquals(5, budget.sent());
        assertEquals(2, budget.dropped());

        budget.reset(2, 5);
        assertTrue(budget.take("a"), "a new tick starts fresh");
        assertEquals(2, budget.dropped(), "dropped counts since the start");
    }

    @Test
    void feetTrailsOnlyDrawWhileWalkingTheOthersAlways() {
        assertFalse(CosmeticEffects.due(Cosmetic.TrailStyle.FEET, 0, false));
        assertTrue(CosmeticEffects.due(Cosmetic.TrailStyle.FEET, 0, true));
        assertFalse(CosmeticEffects.due(Cosmetic.TrailStyle.FEET, 1, true), "every other tick");
        assertTrue(CosmeticEffects.due(Cosmetic.TrailStyle.HALO, CosmeticEffects.HALO_EVERY, false));
        assertTrue(CosmeticEffects.due(Cosmetic.TrailStyle.SPIRAL, CosmeticEffects.SPIRAL_EVERY, false));
    }

    @Test
    void eachStyleSendsAFewPackets() {
        Pos at = new Pos(0, 40, 0);
        assertEquals(1, CosmeticEffects.trail(trail(Cosmetic.TrailStyle.FEET), at, 0).size());
        assertEquals(CosmeticEffects.HALO_POINTS, CosmeticEffects.trail(trail(Cosmetic.TrailStyle.HALO), at, 0).size());
        assertEquals(2, CosmeticEffects.trail(trail(Cosmetic.TrailStyle.SPIRAL), at, 0).size());
        assertEquals(CosmeticEffects.BURST_POINTS, CosmeticEffects.burst(Particle.HEART, at, 1.5).size());
        // A halo is above the head, a feet trail at the feet.
        assertTrue(CosmeticEffects.trail(trail(Cosmetic.TrailStyle.HALO), at, 0).getFirst().y() > at.y() + 2);
        assertTrue(CosmeticEffects.trail(trail(Cosmetic.TrailStyle.FEET), at, 0).getFirst().y() < at.y() + 0.5);
    }

    @Test
    void theLongestChoicesStillFitTheDatabaseColumn() {
        CosmeticChoices longest = CosmeticChoices.NONE.withHideOthers(true);
        for (CosmeticType type : CosmeticType.values()) {
            longest = longest.with(type, "x".repeat(32));
        }
        assertTrue(longest.encode().length() <= 255, longest.encode().length() + " characters");
        assertEquals(longest, CosmeticChoices.decode(longest.encode()));
    }

    @Test
    void theNewStylesDrawWhereTheyBelong() {
        Pos at = new Pos(0, 40, 0);
        List<ParticlePacket> ring = CosmeticEffects.trail(trail(Cosmetic.TrailStyle.RING), at, 0);
        assertEquals(CosmeticEffects.RING_POINTS, ring.size());
        assertTrue(ring.stream().allMatch(p -> p.y() < at.y() + 0.5), "on the ground");

        List<ParticlePacket> wing = CosmeticEffects.trail(trail(Cosmetic.TrailStyle.WINGS), at, 0);
        assertTrue(wing.size() <= 10, "one wing per step fits the usual per-viewer limit");
        assertTrue(wing.stream().allMatch(p -> p.y() > at.y() + 1 && p.z() < at.z()), "on the back (facing +z)");
        List<ParticlePacket> other = CosmeticEffects.trail(trail(Cosmetic.TrailStyle.WINGS), at,
                CosmeticEffects.WINGS_EVERY);
        assertTrue(wing.getFirst().x() * other.getFirst().x() < 0, "the next step draws the other wing");

        Cosmetic.Trail rain = new Cosmetic.Trail(Particle.CLOUD, 3, 0.1, 0, Cosmetic.TrailStyle.CLOUD,
                Particle.DRIPPING_WATER);
        List<ParticlePacket> cloud = CosmeticEffects.trail(rain, at, 0);
        assertEquals(2, cloud.size(), "the cloud and a drop");
        assertTrue(cloud.getFirst().y() > at.y() + 2);
        assertEquals(1, CosmeticEffects.trail(trail(Cosmetic.TrailStyle.CLOUD), at, 0).size(), "no drip, no drop");
    }

    /** A little world: a stone floor at y 39, a stone wall at z 10, and a sign in the wall at y 42. */
    private static final Block.Getter WORLD = (x, y, z, condition) -> {
        if (x == 0 && y == 42 && z == 10) {
            return Block.OAK_SIGN;
        }
        return y == 39 || z == 10 ? Block.STONE : Block.AIR;
    };

    @Test
    void aimingFindsTheFirstSolidBlockInRange() {
        Pos eye = new Pos(0.5, 41.6, 0.5);
        CosmeticEffects.Hit wall = CosmeticEffects.aim(WORLD, eye, new Vec(0, 0, 1), 30);
        assertEquals(new Vec(0, 41, 10), wall.block());
        assertNull(CosmeticEffects.aim(WORLD, eye, new Vec(0, 0, 1), 5), "out of range");
        assertNull(CosmeticEffects.aim(WORLD, eye, new Vec(0, 1, 0), 30), "only sky");
        assertEquals(39, CosmeticEffects.aim(WORLD, eye, new Vec(0, -1, 0), 30).block().blockY(), "the floor");
    }

    @Test
    void paintSkipsAirAndBlockEntitiesAndPutsTheRealBlocksBack() {
        CosmeticEffects.Paint paint = CosmeticEffects.paint(WORLD, new Vec(0, 41, 10), 1, List.of(Block.RED_WOOL),
                new Random(1));
        // Within 1 block: the wall block and its 6 neighbours; the 2 of air and the sign above are skipped.
        assertEquals(4, paint.paint().size());
        assertEquals(paint.paint().size(), paint.restore().size());
        assertTrue(paint.paint().stream().allMatch(p -> ((BlockChangePacket) p).blockStateId()
                == Block.RED_WOOL.stateId()));
        assertTrue(paint.restore().stream().allMatch(p -> ((BlockChangePacket) p).blockStateId()
                == Block.STONE.stateId()));
    }

    @Test
    void petsWalkOnTheFloorAndConfettiFliesForward() {
        assertEquals(40, Followers.ground(WORLD, 0.5, 40, 0.5), "standing on the floor");
        assertEquals(40, Followers.ground(WORLD, 0.5, 42.5, 0.5), "a little higher: down to the floor");
        assertEquals(40, Followers.ground(null, 0.5, 40, 0.5), "no world: the owner's height");

        List<ParticlePacket> confetti = CosmeticEffects.confetti(List.of(NamedTextColor.RED), new Pos(0, 41, 0),
                new Vec(0, 0, 1), 1, new Random(1));
        assertEquals(CosmeticEffects.CONFETTI_PIECES, confetti.size());
        assertTrue(confetti.stream().allMatch(p -> p.particleCount() == 0 && p.offsetZ() > 0), "each piece moves forward");
    }

    private static Cosmetic.Trail trail(Cosmetic.TrailStyle style) {
        return new Cosmetic.Trail(Particle.FLAME, 3, 0.1, 0, style);
    }
}
