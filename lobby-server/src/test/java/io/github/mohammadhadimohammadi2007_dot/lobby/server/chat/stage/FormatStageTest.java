package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.stage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatStageTest {

    private static ChatConfig.Format format(String name, int priority, String... groups) {
        return new ChatConfig.Format(name, priority, Set.of(groups), "<name>: <message>", "", "", List.of(), "");
    }

    /** Highest priority first, as chat.yml is read. */
    private static final List<ChatConfig.Format> FORMATS = List.of(
            format("staff", 100, "admin", "mod"),
            format("vip", 10, "vip"),
            format("default", 0, "default"));

    @Test
    void groupPicksFormat() {
        assertEquals("vip", FormatStage.select(FORMATS, "vip", permission -> false).name());
        assertEquals("staff", FormatStage.select(FORMATS, "mod", permission -> false).name());
    }

    @Test
    void permissionPicksFormat() {
        assertEquals("vip", FormatStage.select(FORMATS, "default",
                permission -> permission.equals("lobby.chat.format.vip")).name());
    }

    @Test
    void higherPriorityWins() {
        assertEquals("staff", FormatStage.select(FORMATS, "vip",
                permission -> permission.equals("lobby.chat.format.staff")).name());
    }

    @Test
    void unknownGroupFallsBackToDefault() {
        assertEquals("default", FormatStage.select(FORMATS, "builder", permission -> false).name());
        assertEquals("vip", FormatStage.select(FORMATS.subList(0, 2), "builder", permission -> false).name());
        assertEquals("default", FormatStage.select(List.of(), "builder", permission -> false).name());
    }
}
