package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.color.TeamColor;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.PlayerListHeaderAndFooterPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tab list, sorting, nametags and the boss bar on a running server. */
@EnvTest
class TabEnvTest {

    private static final String DISPLAY = """
            scoreboard: {enabled: false}
            tab:
              enabled: true
              header: ["<gold>Header %server_online%"]
              footer: ["Footer"]
              name-format: "%luckperms_prefix%%player_name%"
              show: SHOW
              group-order: [admin, vip, default]
            nametags:
              enabled: true
              prefix: "%luckperms_prefix%"
              suffix: ""
              name-color: auto
            bossbar:
              enabled: true
              interval: 40
              messages:
                - text: "First %player_name%"
                  color: red
                - text: "Second"
            join-title: {enabled: false}
            """;

    @TempDir
    Path dir;

    private final List<DisplayTestServer> servers = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(DisplayTestServer::shutdown);
    }

    private DisplayTestServer server(Env env, InstanceContainer map, String show) throws Exception {
        DisplayTestServer server = new DisplayTestServer(env, dir, map, DISPLAY.replace("SHOW", show));
        servers.add(server);
        return server;
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<PlayerInfoUpdatePacket> displayNames(Collector<PlayerInfoUpdatePacket> collector) {
        return collector.collect().stream()
                .filter(p -> p.actions().contains(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)).toList();
    }

    @Test
    void namesGoOutInOnePacketAndOnlyWhenTheyChange(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map, "server");
        TestConnection viewer = DisplayTestServer.join(env, map, "Viewer");
        DisplayTestServer.join(env, map, "Admin");
        DisplayTestServer.join(env, map, "Vip");
        Collector<PlayerInfoUpdatePacket> first = viewer.trackIncoming(PlayerInfoUpdatePacket.class);

        server.displays.refreshNow(2);

        List<PlayerInfoUpdatePacket> sent = displayNames(first);
        assertEquals(1, sent.size(), "every name in one packet");
        Map<String, String> names = sent.getFirst().entries().stream()
                .collect(Collectors.toMap(PlayerInfoUpdatePacket.Entry::username, e -> plain(e.displayName())));
        assertEquals("[Admin] Admin", names.get("Admin"));
        assertEquals("[VIP] Vip", names.get("Vip"));

        Collector<PlayerInfoUpdatePacket> second = viewer.trackIncoming(PlayerInfoUpdatePacket.class);
        server.displays.refreshNow(20);
        assertEquals(List.of(), displayNames(second), "nothing changed, nothing is sent");
    }

    @Test
    void theHeaderAndFooterAreOnlySentWhenTheyChange(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map, "server");
        TestConnection viewer = DisplayTestServer.join(env, map, "Viewer");
        Collector<PlayerListHeaderAndFooterPacket> first = viewer.trackIncoming(PlayerListHeaderAndFooterPacket.class);

        server.displays.refreshNow(2);
        server.displays.refreshNow(20);

        List<PlayerListHeaderAndFooterPacket> sent = first.collect();
        assertEquals(1, sent.size());
        assertEquals("Header 1", plain(sent.getFirst().header()));
        assertEquals("Footer", plain(sent.getFirst().footer()));
    }

    @Test
    void ranksAreSortedByWeightAndTheNametagHasThePrefix(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map, "server");
        DisplayTestServer.join(env, map, "Viewer");
        DisplayTestServer.join(env, map, "Admin");
        TestConnection newcomer = env.createConnection(new net.minestom.server.network.player.GameProfile(
                UUID.randomUUID(), "Vip"));
        Collector<TeamsPacket> teams = newcomer.trackIncoming(TeamsPacket.class);
        newcomer.connect(map, DisplayTestServer.ORIGIN);

        server.displays.refreshNow(2);

        List<String> names = server.teams.teamNames();
        // Admin (weight 100) first, VIP (10) second, the rest after.
        assertTrue(names.stream().anyMatch(name -> name.startsWith("00p")), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.startsWith("01p")), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.startsWith("02p")), names.toString());
        TeamsPacket adminTeam = teams.collect().stream()
                .filter(p -> p.action() instanceof TeamsPacket.CreateTeamAction create
                        && create.entities().contains("Admin"))
                .findFirst().orElseThrow();
        assertTrue(adminTeam.teamName().startsWith("00p"), adminTeam.teamName());
        TeamsPacket.Settings settings = ((TeamsPacket.CreateTeamAction) adminTeam.action()).settings();
        assertEquals("[Admin] ", plain(settings.teamPrefix()));
        assertEquals(TeamColor.RED, settings.color(), "the name takes the prefix's colour");
    }

    @Test
    void sortValuesUseTheGroupOrderWhenAGroupHasNoWeight() {
        List<String> order = List.of("owner", "admin", "default");
        var noWeight = new io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta("", "", "admin",
                Map.of(), 0);
        var weighted = new io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta("", "", "x",
                Map.of(), 50);
        assertEquals(2, PlayerTeams.sortValue(noWeight, order), "second of three from the top");
        assertEquals(50, PlayerTeams.sortValue(weighted, order));
    }

    @Test
    void withShowInstanceOnlyThePlayersOfTheSameInstanceAreListed(Env env) throws Exception {
        InstanceContainer lobby1 = (InstanceContainer) env.createFlatInstance();
        InstanceContainer lobby2 = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, lobby1, "instance");
        TestConnection viewer = DisplayTestServer.join(env, lobby1, "Viewer");
        DisplayTestServer.join(env, lobby1, "Neighbour");
        DisplayTestServer.join(env, lobby2, "Elsewhere");
        Collector<PlayerInfoUpdatePacket> packets = viewer.trackIncoming(PlayerInfoUpdatePacket.class);

        server.displays.refreshNow(2);

        Set<String> hidden = packets.collect().stream()
                .filter(p -> p.actions().contains(PlayerInfoUpdatePacket.Action.UPDATE_LISTED))
                .flatMap(p -> p.entries().stream()).filter(e -> !e.listed())
                .map(PlayerInfoUpdatePacket.Entry::username).collect(Collectors.toSet());
        assertEquals(Set.of("Elsewhere"), hidden);
    }

    @Test
    void theBossBarRotatesThroughItsMessages(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        DisplayTestServer server = server(env, map, "server");
        TestConnection viewer = DisplayTestServer.join(env, map, "Viewer");
        Collector<BossBarPacket> bars = viewer.trackIncoming(BossBarPacket.class);

        server.displays.refreshNow(2);
        server.displays.refreshNow(40);

        List<BossBarPacket> sent = bars.collect();
        // Each player has their own offset within the interval, so either message can come first.
        String first = plain(((BossBarPacket.AddAction) sent.getFirst().action()).title());
        List<String> titles = sent.stream().filter(p -> p.action() instanceof BossBarPacket.UpdateTitleAction)
                .map(p -> plain(((BossBarPacket.UpdateTitleAction) p.action()).title())).toList();
        assertEquals(1, titles.size(), "one change after one interval: " + titles);
        assertEquals(java.util.Set.of("First Viewer", "Second"), java.util.Set.of(first, titles.getFirst()),
                "it moved on to the other message");
    }
}
