package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyCombatTagsTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final ProxyCombatTags tags = new ProxyCombatTags(now::get);
    private final UUID steve = UUID.randomUUID();

    @Test
    void aTaggedPlayerCannotLeaveTheirLobbyUntilTheCombatEnds() {
        tags.update(steve, "lobby-1", 10_000, "<red>wait <seconds>s");

        ProxyCombatTags.Tag tag = tags.blocking(steve, "lobby-1");
        assertNotNull(tag);
        assertEquals(10, tags.secondsLeft(tag));
        assertEquals("<red>wait <seconds>s", tag.message());

        now.addAndGet(9_500);
        assertEquals(1, tags.secondsLeft(tags.blocking(steve, "lobby-1")));
        now.addAndGet(500);
        assertNull(tags.blocking(steve, "lobby-1"), "over");
    }

    @Test
    void endingEarlyOrBeingOnAnotherServerLetsThemGo() {
        tags.update(steve, "lobby-1", 10_000, "x");
        tags.update(steve, "lobby-1", 0, "");
        assertNull(tags.blocking(steve, "lobby-1"));

        tags.update(steve, "lobby-1", 10_000, "x");
        assertNull(tags.blocking(steve, "lobby-2"), "only the lobby that reported it counts");
        assertNull(tags.blocking(steve, "lobby-1"), "and the stale tag is gone");

        tags.update(steve, "lobby-1", 10_000, "x");
        tags.forget(steve);
        assertNull(tags.blocking(steve, "lobby-1"));
    }
}
