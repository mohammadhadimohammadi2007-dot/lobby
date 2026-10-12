package io.github.mohammadhadimohammadi2007_dot.lobby.bridge.velocity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerCommandsTest {

    @Test
    void theSlashAndSpacesAreRemoved() {
        assertEquals("server bedwars", PlayerCommands.normalize("  /server bedwars "));
        assertEquals("queue bw", PlayerCommands.normalize("queue bw"));
    }

    @Test
    void emptyLongAndMultiLineCommandsAreRefused() {
        assertNull(PlayerCommands.normalize(""));
        assertNull(PlayerCommands.normalize(" / "));
        assertNull(PlayerCommands.normalize("say hi\nop Steve"));
        assertNull(PlayerCommands.normalize("say \u0000"));
        assertNull(PlayerCommands.normalize("x".repeat(PlayerCommands.MAX_LENGTH + 1)));
        assertEquals("x".repeat(PlayerCommands.MAX_LENGTH), PlayerCommands.normalize("x".repeat(PlayerCommands.MAX_LENGTH)));
    }
}
