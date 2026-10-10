package io.github.mohammadhadimohammadi2007_dot.lobby.server.team;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
class TeamManagerTest {

    private final Map<String, Integer> protocols = new HashMap<>();

    private TeamManager manager() {
        return new TeamManager(player -> protocols.getOrDefault(player.getUsername(), MinecraftServer.PROTOCOL_VERSION), false);
    }

    private record Joined(Player player, Collector<TeamsPacket> teams) {
    }

    private Joined join(Env env, Instance instance, String name) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), name));
        Player player = connection.connect(instance, new Pos(0, 41, 0));
        return new Joined(player, connection.trackIncoming(TeamsPacket.class));
    }

    @Test
    void legacyLimitCutsWithoutBreakingColors() {
        Component prefix = Component.text("[Owner] ", NamedTextColor.DARK_RED).append(Component.text("Very long", NamedTextColor.GOLD));
        Component cut = LegacyText.limit(prefix, TeamManager.LEGACY_LIMIT);
        String legacy = LegacyText.serialize(cut);
        assertTrue(legacy.length() <= TeamManager.LEGACY_LIMIT, legacy);
        assertTrue(!legacy.endsWith("§"), legacy);
        assertEquals("§4[Owner] §6Very", legacy);
        assertEquals("ab", LegacyText.cut("ab§cd", 4));
        assertEquals("ab", LegacyText.cut("ab§cd", 3));
        Component shortText = Component.text("[VIP] ", NamedTextColor.GOLD);
        assertEquals(shortText, LegacyText.limit(shortText, TeamManager.LEGACY_LIMIT));
    }

    @Test
    void playersNpcsAndSidebarDoNotClash(Env env) {
        TeamManager teams = manager();
        Instance instance = env.createFlatInstance();
        protocols.put("Old", ProtocolVersions.V1_8);
        Joined steve = join(env, instance, "Steve");
        Joined old = join(env, instance, "Old");

        teams.setPlayer(steve.player(), 5, Component.text("[Admin-of-everything] "), Component.empty(), TeamColor.RED);
        teams.setPlayer(old.player(), 50, Component.text("[VIP] "), Component.empty(), TeamColor.GOLD);
        teams.hideName("npc_0001");

        List<String> names = teams.teamNames();
        assertEquals(List.of("zzhidden", "05psteve", "50pold"), names);
        // Tab sorts by team name: staff (05) before VIP (50); hidden NPC names last.
        assertTrue(names.get(1).compareTo(names.get(2)) < 0 && names.get(2).compareTo(names.get(0)) < 0);

        TeamsPacket forModern = createOf(steve.teams().collect(), "05psteve");
        TeamsPacket forLegacy = createOf(old.teams().collect(), "05psteve");
        TeamsPacket.CreateTeamAction modern = (TeamsPacket.CreateTeamAction) forModern.action();
        TeamsPacket.CreateTeamAction legacy = (TeamsPacket.CreateTeamAction) forLegacy.action();
        assertEquals("[Admin-of-everything] ", LegacyText.serialize(modern.settings().teamPrefix()));
        assertEquals("[Admin-of-everyt", LegacyText.serialize(legacy.settings().teamPrefix()));
        assertEquals(List.of("Steve"), modern.entities());
        assertEquals(TeamsPacket.CollisionRule.NEVER, modern.settings().collisionRule());
    }

    @Test
    void changingOrderReplacesTeamAndSameValuesSendNothing(Env env) {
        TeamManager teams = manager();
        Joined steve = join(env, env.createFlatInstance(), "Steve");

        teams.setPlayer(steve.player(), 10, Component.text("A"), Component.empty(), TeamColor.WHITE);
        teams.setPlayer(steve.player(), 10, Component.text("A"), Component.empty(), TeamColor.WHITE);
        teams.setPlayer(steve.player(), 10, Component.text("B"), Component.empty(), TeamColor.WHITE);
        teams.setPlayer(steve.player(), 20, Component.text("B"), Component.empty(), TeamColor.WHITE);
        teams.removePlayer(steve.player());

        List<String> actions = steve.teams().collect().stream()
                .map(packet -> packet.teamName() + ":" + packet.action().getClass().getSimpleName()).toList();
        assertEquals(List.of("10psteve:CreateTeamAction", "10psteve:UpdateTeamAction", "10psteve:RemoveTeamAction",
                "20psteve:CreateTeamAction", "20psteve:RemoveTeamAction"), actions);
        assertEquals(List.of("zzhidden"), teams.teamNames());
    }

    @Test
    void playersOfTheSameRankAreSortedByNameIgnoringCase(Env env) {
        TeamManager teams = manager();
        Instance instance = env.createFlatInstance();
        for (String name : List.of("zed", "Alex", "bob", "Carl_99", "VeryLongName1234")) {
            teams.setPlayer(join(env, instance, name).player(), 3, Component.empty(), Component.empty(), TeamColor.WHITE);
        }
        Joined clash = join(env, instance, "VeryLongName1299");
        teams.setPlayer(clash.player(), 3, Component.empty(), Component.empty(), TeamColor.WHITE);

        List<String> names = teams.teamNames().stream().filter(name -> !name.startsWith("zz")).sorted().toList();
        assertEquals(List.of("03palex", "03pbob", "03pcarl_99", "03pverylongname1", "03pverylongnam~0", "03pzed"), names,
                "sorted like a client sorts teams: by name, case ignored; a clash after the cut gets the id");
        assertTrue(names.stream().allMatch(name -> name.length() <= 16), names.toString());
    }

    @Test
    void newPlayersGetEveryTeam(Env env) {
        TeamManager teams = manager();
        Instance instance = env.createFlatInstance();
        Joined steve = join(env, instance, "Steve");
        teams.setPlayer(steve.player(), 1, Component.text("x"), Component.empty(), TeamColor.WHITE);
        teams.hideName("npc_1");
        Joined alex = join(env, instance, "Alex");

        teams.sendAllTo(alex.player());

        List<TeamsPacket> packets = alex.teams().collect();
        assertEquals(List.of("zzhidden", "01psteve"), packets.stream().map(TeamsPacket::teamName).toList());
        TeamsPacket.CreateTeamAction hidden = (TeamsPacket.CreateTeamAction) packets.getFirst().action();
        assertEquals(List.of("npc_1"), hidden.entities());
        assertEquals(TeamsPacket.NameTagVisibility.NEVER, hidden.settings().nameTagVisibility());
    }

    private static TeamsPacket createOf(List<TeamsPacket> packets, String team) {
        return packets.stream().filter(packet -> packet.teamName().equals(team)
                && packet.action() instanceof TeamsPacket.CreateTeamAction).findFirst().orElseThrow();
    }
}
