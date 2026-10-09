package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FancyNpcs actions turned into this lobby's, as its own action classes behave. */
class FancyNpcsActionsTest {

    private static FancyNpcsActions.Action action(String type, String value) {
        return new FancyNpcsActions.Action(type, value);
    }

    @Test
    void simpleActionsKeepTheirMeaning() {
        List<String> problems = new ArrayList<>();

        List<Object> entries = FancyNpcsActions.list(List.of(
                action("message", "<green>Hello"),
                action("send_to_server", "bedwars-1"),
                action("play_sound", "entity.experience_orb.pickup"),
                action("player_command", "spawn"),
                action("console_command", "say hi"),
                action("need_permission", "lobby.vip")), problems);

        assertEquals(List.of("message: <green>Hello", "connect: bedwars-1", "sound: entity.experience_orb.pickup",
                "player_command: spawn", "console_command: say hi", "need_permission: lobby.vip"), entries);
        assertEquals(List.of(), problems);
    }

    @Test
    void waitIsSecondsThereAndTicksHere() {
        // FancyNpcs' WaitAction sleeps value * 1000 ms.
        assertEquals(List.of("wait: 60"), FancyNpcsActions.list(List.of(action("wait", "3")), new ArrayList<>()));
    }

    @Test
    void aRandomActionTakesEverythingAfterIt() {
        // FancyNpcs runs one of the actions after execute_random_action and then stops.
        List<Object> entries = FancyNpcsActions.list(List.of(
                action("message", "first"),
                action("execute_random_action", ""),
                action("message", "a"),
                action("message", "b")), new ArrayList<>());

        assertEquals(List.of("message: first", Map.of("random", List.of("message: a", "message: b"))), entries);
    }

    @Test
    void anOperatorCommandIsRefusedAndReported() {
        List<String> problems = new ArrayList<>();

        List<Object> entries = FancyNpcsActions.list(List.of(
                action("player_command_as_op", "gamemode creative"),
                action("block_until_done", ""),
                action("message", "kept")), problems);

        assertEquals(List.of("message: kept"), entries);
        assertEquals(1, problems.size(), "block_until_done is not needed, so only the operator command is reported");
        assertTrue(problems.getFirst().contains("operator"), problems.toString());
    }

    @Test
    void whatAnAdminTypesInEitherForm() throws Exception {
        assertEquals("message: Hello there", FancyNpcsActions.typed("message: Hello there"), "this lobby's form");
        assertEquals("message: Hello there", FancyNpcsActions.typed("message Hello there"), "FancyNpcs' form");
        assertEquals("connect: lobby-2", FancyNpcsActions.typed("send_to_server lobby-2"));
        assertEquals("wait: 40", FancyNpcsActions.typed("wait 2"), "without a colon it is FancyNpcs' seconds");
        assertEquals("wait: 2", FancyNpcsActions.typed("wait: 2"), "with a colon it is this lobby's ticks");
        assertEquals("close_menu", FancyNpcsActions.typed("close_menu"));
        assertThrows(ActionParser.ActionException.class, () -> FancyNpcsActions.typed("player_command_as_op op me"));
        assertThrows(ActionParser.ActionException.class, () -> FancyNpcsActions.typed("fly away"));
    }
}
