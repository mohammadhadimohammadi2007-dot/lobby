package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandGateTest {

    private final CommandGate gate = new CommandGate(Set.of("mute", "tempmute", "warn"));

    @Test
    void allowsListedCommands() {
        assertNull(gate.refusalReason("tempmute Steve 10m Spam"));
        assertNull(gate.refusalReason("/mute Steve"));
        assertNull(gate.refusalReason("  WARN Steve caps"));
    }

    @Test
    void refusesEverythingElse() {
        assertNotNull(gate.refusalReason("op Steve"));
        assertNotNull(gate.refusalReason("lpv user Steve permission set *"));
        assertNotNull(gate.refusalReason("mutex Steve"), "only the whole first word counts");
        assertNotNull(gate.refusalReason(""));
        assertNotNull(gate.refusalReason("mute Steve\nop Steve"), "no second command through a line break");
        assertNotNull(gate.refusalReason("mute " + "x".repeat(CommandGate.MAX_LENGTH)));
    }

    @Test
    void normalizeRemovesSlashAndSpaces() {
        assertEquals("mute Steve", CommandGate.normalize("  /mute Steve "));
    }
}
