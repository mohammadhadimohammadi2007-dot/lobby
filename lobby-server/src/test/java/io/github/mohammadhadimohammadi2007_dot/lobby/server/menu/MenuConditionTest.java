package io.github.mohammadhadimohammadi2007_dot.lobby.server.menu;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuCondition.ConditionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuConditionTest {

    @Test
    void readsBothForms() throws ConditionException {
        assertEquals(new MenuCondition.HasPermission("lobby.vip"), MenuCondition.parse(" permission: lobby.vip "));
        assertEquals(new MenuCondition.Compare("%server_status_bedwars%", MenuCondition.Operator.EQUAL, "offline"),
                MenuCondition.parse("%server_status_bedwars% == offline"));
        assertEquals(new MenuCondition.Compare("%server_online%", MenuCondition.Operator.GREATER_OR_EQUAL, "10"),
                MenuCondition.parse("%server_online% >= 10"));
        assertEquals(new MenuCondition.Compare("%player_name%", MenuCondition.Operator.CONTAINS, "a"),
                MenuCondition.parse("%player_name% contains a"));
    }

    @Test
    void longerOperatorsWinOverShorterOnes() throws ConditionException {
        assertEquals(MenuCondition.Operator.GREATER_OR_EQUAL,
                ((MenuCondition.Compare) MenuCondition.parse("%a% >= 1")).operator());
        assertEquals(MenuCondition.Operator.LESS_OR_EQUAL,
                ((MenuCondition.Compare) MenuCondition.parse("%a% <= 1")).operator());
        assertEquals(MenuCondition.Operator.NOT_EQUAL,
                ((MenuCondition.Compare) MenuCondition.parse("%a% != 1")).operator());
    }

    @Test
    void describeRoundTrips() throws ConditionException {
        for (String line : java.util.List.of("permission: lobby.vip", "%server_online% > 10",
                "%server_status_bedwars% == online", "%player_name% contains a")) {
            assertEquals(line, MenuCondition.parse(line).describe());
        }
    }

    @Test
    void clearErrors() {
        assertThrows(ConditionException.class, () -> MenuCondition.parse("permission:"));
        assertThrows(ConditionException.class, () -> MenuCondition.parse("always"));
        assertThrows(ConditionException.class, () -> MenuCondition.parse("== offline"));
        assertThrows(ConditionException.class, () -> MenuCondition.parse("%a% =="));
    }
}
