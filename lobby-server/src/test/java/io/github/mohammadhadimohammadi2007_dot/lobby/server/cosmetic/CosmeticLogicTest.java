package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.particle.Particle;
import org.junit.jupiter.api.Test;

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

    private static Cosmetic.Trail trail(Cosmetic.TrailStyle style) {
        return new Cosmetic.Trail(Particle.FLAME, 3, 0.1, 0, style);
    }
}
