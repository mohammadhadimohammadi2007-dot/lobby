package io.github.mohammadhadimohammadi2007_dot.lobby.server.action;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser.ActionException;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConnectAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.ConsoleCommandAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.MessageAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.NeedPermissionAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.RandomAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.SoundAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TeleportAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TitleAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.TransferAction;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.type.WaitAction;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionParserTest {

    @Test
    void readsEveryType() throws ActionException {
        assertEquals(new MessageAction("<gold>Hi %player_name%"), ActionParser.parse("message: <gold>Hi %player_name%"));
        assertEquals(new ConnectAction("bedwars", true), ActionParser.parse("connect_group: bedwars"));
        assertEquals(new ConnectAction("lobby-2", false), ActionParser.parse("CONNECT : lobby-2"));
        assertEquals(new ConsoleCommandAction("lobby info"), ActionParser.parse("console_command: /lobby info"));
        assertEquals(new WaitAction(20), ActionParser.parse("wait: 20"));
        assertEquals(new TeleportAction(null), ActionParser.parse("teleport: spawn"));
        assertEquals(new TeleportAction(new Pos(1, 2.5, 3, 90, 0)), ActionParser.parse("teleport: 1 2.5 3 90 0"));
        assertEquals(new NeedPermissionAction("lobby.vip", "<red>VIP only"), ActionParser.parse("need_permission: lobby.vip <red>VIP only"));
        assertEquals(new NeedPermissionAction("lobby.vip", null), ActionParser.parse("need_permission: lobby.vip"));
        assertEquals(new TransferAction("play.example.com", 25566), ActionParser.parse("transfer: play.example.com:25566"));
        assertEquals(new TransferAction("play.example.com", 25565), ActionParser.parse("transfer: play.example.com"));
        assertEquals(new TitleAction("<gold>Hi", "<gray>sub", 5, 40, 5), ActionParser.parse("title: <gold>Hi | <gray>sub | 5 40 5"));
        assertEquals(new TitleAction("Hi", "", 10, 70, 20), ActionParser.parse("title: Hi"));
        SoundAction sound = (SoundAction) ActionParser.parse("sound: entity.experience_orb.pickup 0.5 1.5");
        assertEquals("minecraft:entity.experience_orb.pickup", sound.sound().asString());
        assertEquals(1.5f, sound.pitch());
        for (String name : ActionParser.typeNames()) {
            assertTrue(name.equals("random") || !name.isBlank());
        }
    }

    @Test
    void messagesKeepColonsInTheText() throws ActionException {
        assertEquals(new MessageAction("Time: 12:00"), ActionParser.parse("message: Time: 12:00"));
    }

    @Test
    void randomTakesAList() throws ActionException {
        Action random = ActionParser.parse(Map.of("random", List.of("message: a", "message: b")));
        assertInstanceOf(RandomAction.class, random);
        assertEquals(2, ((RandomAction) random).options().size());
        assertEquals(random, ActionParser.parse(ActionParser.toConfig(random)));
        assertThrows(ActionException.class, () -> ActionParser.parse("random: a"));
        assertThrows(ActionException.class, () -> ActionParser.parse(Map.of("random", List.of())));
    }

    @Test
    void describeRoundTrips() throws ActionException {
        for (String line : List.of("message: Hello", "player_command: spawn", "console_command: lobby info",
                "connect: bedwars-1", "connect_group: bedwars", "lobby: 2", "open_menu: servers", "close_menu",
                "actionbar: <green>Hi", "teleport: spawn", "wait: 40", "need_permission: lobby.vip",
                "transfer: play.example.com 25565", "sound: block.note_block.pling 1.0 2.0",
                "title: A | B | 1 2 3", "teleport: 1.0 2.0 3.0 4.0 5.0")) {
            Action action = ActionParser.parse(line);
            assertEquals(action, ActionParser.parse(action.describe()), line);
        }
    }

    @Test
    void clearErrors() {
        assertEquals("unknown action 'conect' (did you mean 'connect'?)",
                assertThrows(ActionException.class, () -> ActionParser.parse("conect: bedwars")).getMessage());
        assertTrue(assertThrows(ActionException.class, () -> ActionParser.parse("explode: now")).getMessage()
                .contains("Known actions: message"));
        assertThrows(ActionException.class, () -> ActionParser.parse("message:"));
        assertThrows(ActionException.class, () -> ActionParser.parse("wait: soon"));
        assertThrows(ActionException.class, () -> ActionParser.parse("wait: 0"));
        assertThrows(ActionException.class, () -> ActionParser.parse("lobby: 0"));
        assertThrows(ActionException.class, () -> ActionParser.parse("connect: two words"));
        assertThrows(ActionException.class, () -> ActionParser.parse("teleport: 1 2"));
        assertThrows(ActionException.class, () -> ActionParser.parse("sound: Not A Key"));
        assertThrows(ActionException.class, () -> ActionParser.parse("sound: ui.button.click 1 9"));
        assertThrows(ActionException.class, () -> ActionParser.parse("transfer: host 99999"));
        assertThrows(ActionException.class, () -> ActionParser.parse("title: a | b | 1 2"));
        assertThrows(ActionException.class, () -> ActionParser.parse((Object) 42));
    }

    @Test
    void listSkipsBrokenEntriesAndNamesThem() {
        List<String> warnings = new ArrayList<>();
        ActionList list = ActionParser.parseList(List.of("message: ok", "conect: x", "wait: 5"), 500,
                "menus.yml: servers.items.bedwars.actions", warnings::add);

        assertEquals(2, list.actions().size());
        assertEquals(500, list.cooldownMillis());
        assertEquals(List.of("menus.yml: servers.items.bedwars.actions (entry 2): unknown action 'conect'"
                + " (did you mean 'connect'?). This action is skipped."), warnings);
    }

    @Test
    void cooldownPerPlayer() {
        ActionList list = new ActionList(List.of(new WaitAction(1)), 1000);
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();

        assertTrue(list.passCooldown(steve, 10_000));
        assertFalse(list.passCooldown(steve, 10_500));
        assertTrue(list.passCooldown(alex, 10_500));
        assertTrue(list.passCooldown(steve, 11_000));
        assertTrue(new ActionList(List.of(), 0).passCooldown(steve, 0));
    }
}
