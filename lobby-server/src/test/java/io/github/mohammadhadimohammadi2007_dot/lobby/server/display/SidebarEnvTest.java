package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.DisplayScoreboardPacket;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.packet.server.play.UpdateScorePacket;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sidebar on a running server: what each client version is sent, and that only changes are sent. */
@EnvTest
class SidebarEnvTest {

    private static final String BOARD = """
            scoreboard:
              enabled: true
              boards:
                default:
                  title: ["<gold>LOBBY"]
                  lines:
                    - "<gold>Hello %player_name%"
                    - "Online: %server_online%"
                    - ""
                    - text: "Slow: %server_online%"
                      interval: 100
                    - "<gold>abcdefghijklmnopqrstuvwxyz0123456789"
                staff:
                  priority: 10
                  permission: lobby.staff
                  title: ["<red>STAFF"]
                  lines: ["staff only"]
            tab: {enabled: false}
            nametags: {enabled: false}
            bossbar: {enabled: false}
            join-title: {enabled: false}
            """;

    @TempDir
    Path dir;

    private final List<DisplayTestServer> servers = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(DisplayTestServer::shutdown);
    }

    private DisplayTestServer server(Env env, InstanceContainer map) throws Exception {
        DisplayTestServer server = new DisplayTestServer(env, dir, map, BOARD);
        servers.add(server);
        return server;
    }

    private static List<UpdateScorePacket> scores(List<ServerPacket> packets) {
        return packets.stream().filter(UpdateScorePacket.class::isInstance).map(UpdateScorePacket.class::cast).toList();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void aNewClientGetsLinesAsScoreNamesWithTheNumbersHidden(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        // A guest has no permissions, so the staff board (higher priority) is not theirs.
        TestConnection guest = DisplayTestServer.join(env, map, "Guest");
        Collector<ServerPacket> packets = guest.trackIncoming(ServerPacket.class);

        server.displays.refreshNow(2);

        List<ServerPacket> all = packets.collect();
        ScoreboardObjectivePacket objective = all.stream().filter(ScoreboardObjectivePacket.class::isInstance)
                .map(ScoreboardObjectivePacket.class::cast).findFirst().orElseThrow();
        assertEquals("LOBBY", plain(objective.objectiveValue()));
        assertEquals(Sidebar.NumberFormat.blank(), objective.numberFormat(), "the red numbers are hidden on 1.20.3+");
        assertTrue(all.stream().anyMatch(p -> p instanceof DisplayScoreboardPacket display && display.position() == 1));
        List<UpdateScorePacket> lines = scores(all);
        assertEquals(5, lines.size());
        assertEquals("Hello Guest", plain(lines.getFirst().displayName()));
        assertEquals(5, lines.getFirst().score(), "the first line has the highest score, so it is on top");
        assertTrue(all.stream().noneMatch(TeamsPacket.class::isInstance), "1.20.3+ needs no team per line");
    }

    @Test
    void onlyLinesThatChangedAreSentAgain(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        TestConnection guest = DisplayTestServer.join(env, map, "Guest");
        server.displays.refreshNow(2);

        Collector<ServerPacket> nothing = guest.trackIncoming(ServerPacket.class);
        server.displays.refreshNow(20);
        assertEquals(List.of(), scores(nothing.collect()), "nothing changed, so nothing is sent");

        // One more player: over the next 100 ticks the player count line changes once, and the slow line
        // (every 100 ticks) catches up once; nothing else is sent.
        DisplayTestServer.join(env, map, "Guest2");
        Collector<ServerPacket> changed = guest.trackIncoming(ServerPacket.class);
        for (int i = 0; i < 5; i++) {
            // The player count is cached for a second; these refreshes are closer together than that.
            server.placeholders.invalidateAll();
            server.displays.refreshNow(20);
        }
        List<String> sent = scores(changed.collect()).stream().map(p -> plain(p.displayName())).toList();
        assertEquals(List.of("Online: 2", "Slow: 2"), sent.stream().sorted().toList(), "only the two changed lines");
    }

    @Test
    void theBoardIsChosenByPermissionAndPriority(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        TestConnection staff = DisplayTestServer.join(env, map, "Staffer");
        Collector<ScoreboardObjectivePacket> objectives = staff.trackIncoming(ScoreboardObjectivePacket.class);

        server.displays.refreshNow(2);

        assertEquals("STAFF", plain(objectives.collect().getFirst().objectiveValue()));
    }

    @Test
    void clientsBefore1203GetOneTeamPerLine(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        TestConnection mid = DisplayTestServer.join(env, map, "GuestMid");
        Collector<ServerPacket> packets = mid.trackIncoming(ServerPacket.class);

        server.displays.refreshNow(2);

        List<ServerPacket> all = packets.collect();
        List<UpdateScorePacket> lines = scores(all);
        assertEquals(5, lines.size());
        lines.forEach(line -> assertNull(line.displayName(), "these clients would ignore a display name"));
        TeamsPacket first = teamOf(all, SidebarPackets.entry(0));
        TeamsPacket.CreateTeamAction create = (TeamsPacket.CreateTeamAction) first.action();
        assertEquals("Hello GuestMid", plain(create.settings().teamPrefix()), "the whole line is the team prefix");
        TeamsPacket longLine = teamOf(all, SidebarPackets.entry(4));
        assertEquals("abcdefghijklmnopqrstuvwxyz0123456789",
                plain(((TeamsPacket.CreateTeamAction) longLine.action()).settings().teamPrefix()),
                "1.13+ has no 16-character limit");
    }

    @Test
    void oneEightGetsLongLinesSplitOverPrefixAndSuffix(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        TestConnection old = DisplayTestServer.join(env, map, "GuestOld");
        Collector<TeamsPacket> teams = old.trackIncoming(TeamsPacket.class);

        server.displays.refreshNow(2);

        TeamsPacket longLine = teams.collect().stream()
                .filter(p -> p.action() instanceof TeamsPacket.CreateTeamAction create
                        && create.entities().contains(SidebarPackets.entry(4)))
                .findFirst().orElseThrow();
        TeamsPacket.Settings settings = ((TeamsPacket.CreateTeamAction) longLine.action()).settings();
        String prefix = LegacyText.serialize(settings.teamPrefix());
        String suffix = LegacyText.serialize(settings.teamSuffix());
        assertTrue(prefix.length() <= TeamManager.LEGACY_LIMIT, prefix);
        assertTrue(suffix.length() <= TeamManager.LEGACY_LIMIT, suffix);
        assertTrue(suffix.startsWith("§6"), "the suffix carries the colour on: " + suffix);
        // 14 letters after the colour code in each part.
        assertEquals("abcdefghijklmnopqrstuvwxyz01", plain(settings.teamPrefix()) + plain(settings.teamSuffix()),
                "as much of the line as fits in two times 16 characters");
    }

    @Test
    void turningTheScoreboardOffRemovesIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map);
        TestConnection guest = DisplayTestServer.join(env, map, "Guest");
        server.displays.refreshNow(2);
        java.nio.file.Files.writeString(dir.resolve("display.yml"), BOARD.replace("scoreboard:\n  enabled: true",
                "scoreboard:\n  enabled: false"));
        server.config.reload();
        Collector<ScoreboardObjectivePacket> objectives = guest.trackIncoming(ScoreboardObjectivePacket.class);

        server.displays.refreshNow(20);

        ScoreboardObjectivePacket removed = objectives.collect().stream().findFirst().orElse(null);
        assertNotNull(removed, "the objective is removed");
        assertEquals(1, removed.mode());
    }

    private static TeamsPacket teamOf(List<ServerPacket> packets, String entry) {
        return packets.stream().filter(TeamsPacket.class::isInstance).map(TeamsPacket.class::cast)
                .filter(p -> p.action() instanceof TeamsPacket.CreateTeamAction create && create.entities().contains(entry))
                .findFirst().orElseThrow(() -> new AssertionError("no team for line " + entry));
    }
}
