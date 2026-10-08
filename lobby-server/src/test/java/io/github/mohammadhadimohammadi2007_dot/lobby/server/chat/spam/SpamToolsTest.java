package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamToolsTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void tokenBucketAllowsBurstThenRefills() {
        TokenBucket bucket = new TokenBucket(3, 2, 0);

        assertTrue(bucket.tryConsume(0));
        assertTrue(bucket.tryConsume(0));
        assertTrue(bucket.tryConsume(0));
        assertFalse(bucket.tryConsume(0), "burst used up");
        assertEquals(2.0, bucket.secondsUntilNext(0), 1e-9);
        assertFalse(bucket.tryConsume(SECOND), "half a token after one second");
        assertTrue(bucket.tryConsume(2 * SECOND), "one token after two seconds");
    }

    @Test
    void tokenBucketWithoutCooldownNeverBlocks() {
        TokenBucket bucket = new TokenBucket(1, 0, 0);
        for (int i = 0; i < 100; i++) {
            assertTrue(bucket.tryConsume(0));
        }
    }

    @Test
    void reconfigureKeepsUsedTokens() {
        TokenBucket bucket = new TokenBucket(5, 1, 0);
        bucket.tryConsume(0);
        bucket.configure(2, 1);
        assertTrue(bucket.tryConsume(0));
        assertTrue(bucket.tryConsume(0));
        assertFalse(bucket.tryConsume(0));
    }

    @Test
    void capsIgnoresPersian() {
        assertEquals(100, SpamChecks.capsPercent("HELLO EVERYONE"));
        assertEquals(40, SpamChecks.capsPercent("HEllo"));
        assertEquals(0, SpamChecks.capsPercent("سلام به همه"));
        assertEquals(100, SpamChecks.capsPercent("سلام HELLO"));
        assertEquals(5, SpamChecks.casedLetters("سلام HELLO"));
    }

    @Test
    void repeatedCharactersAreShortened() {
        assertEquals("nooo!!!", SpamChecks.limitRepeats("noooooooo!!!!!!!", 3));
        assertEquals("ok", SpamChecks.limitRepeats("ok", 3));
        assertEquals("سلاااام", SpamChecks.limitRepeats("سلاااااااام", 4));
    }

    @Test
    void nearDuplicates() {
        assertTrue(SpamChecks.isNearDuplicate("join my server pls", List.of("join my server pls!"), 0.85));
        assertFalse(SpamChecks.isNearDuplicate("good game", List.of("hello", "anyone here?"), 0.85));
    }

    @Test
    void violationPointsDecay() {
        ViolationTracker tracker = new ViolationTracker();
        UUID player = UUID.randomUUID();

        assertEquals(5, tracker.add(player, 5, 1, 0), 1e-9);
        assertEquals(8, tracker.add(player, 5, 1, 2 * 60 * SECOND), 1e-9, "two points decayed after two minutes");
        assertEquals(0, tracker.current(player, 1, 60 * 60 * SECOND), 1e-9, "never below zero");
        tracker.reset(player);
        assertEquals(0, tracker.current(player, 1, 0), 1e-9);
    }
}
