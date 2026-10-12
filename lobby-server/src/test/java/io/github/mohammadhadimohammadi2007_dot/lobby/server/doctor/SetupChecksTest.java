package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the setup checklist and the doctor say for typical situations. */
class SetupChecksTest {

    private final SetupChecks checks = new SetupChecks();

    /** A finished network lobby; tests change what they need. */
    private static SetupFacts network() {
        return new SetupFacts(ConnectionMode.VELOCITY, false, true, 0, WorldFormat.POLAR, "lobby", "worlds/lobby",
                529, false, true, 2, true, true, 1000,
                Map.of("bedwars", List.of("bw-1"), "lobbies", List.of("lobby-1")),
                Optional.of(new BridgeMessage.ProxyInfo("0.4.0", "Velocity 3.5.1", true, true, true, true)),
                12, 600_000, List.of(IntegrationStatus.active("LuckPerms", "messaging: sql")), null,
                NetworkCheck.Report.NOT_CHECKED, Set.of("bedwars"), Set.of("servers", "lobby"), true, true, true,
                Map.of(), Map.of(), List.of());
    }

    private static Check find(List<Check> list, String title) {
        return list.stream().filter(check -> check.title().equals(title)).findFirst()
                .orElseThrow(() -> new AssertionError("no '" + title + "' in " + list));
    }

    @Test
    void aFinishedNetworkLobbyIsAllGreen() {
        List<Check> setup = checks.setup(network());
        List<Check> notOk = setup.stream().filter(check -> check.status() != Check.Status.OK).toList();
        assertEquals(List.of(), notOk);
        assertEquals("connected; groups: bedwars, lobbies", find(setup, "Proxy bridge").detail());
        assertEquals(List.of(Check.ok("Nothing wrong found", "run /setup-lobby setup to see what is not set up yet")),
                checks.doctor(network()));
    }

    @Test
    void aFreshStandaloneServerSaysWhatToDoNext() {
        SetupFacts fresh = new SetupFacts(ConnectionMode.STANDALONE, false, false, 0, WorldFormat.FLAT_FALLBACK,
                "flat platform", "worlds/lobby", 361, true, true, 0, false, false, -1, Map.of(), Optional.empty(), 0,
                1000, List.of(IntegrationStatus.disabled("LuckPerms")), null, NetworkCheck.Report.NOT_CHECKED, Set.of(),
                Set.of("servers"), true, true, true, Map.of(), Map.of(), List.of());

        List<Check> setup = checks.setup(fresh);

        assertEquals(Check.Status.FAIL, find(setup, "Map").status());
        assertTrue(find(setup, "Map").detail().contains("worlds/lobby"));
        assertEquals(Check.Status.OK, find(setup, "Spawn").status(), "the flat platform's spawn is fine");
        assertEquals("/op ", find(setup, "Operators").command());
        assertEquals(Check.Status.WARN, find(setup, "Connection").status());
        assertEquals(Check.Status.INFO, find(setup, "LuckPerms").status());
        assertTrue(setup.stream().noneMatch(check -> check.title().equals("Proxy bridge")), "no proxy, no bridge line");
    }

    @Test
    void aDefaultSpawnOnARealMapIsFlagged() {
        SetupFacts facts = new SetupFacts(ConnectionMode.STANDALONE, true, false, 0, WorldFormat.POLAR, "lobby",
                "worlds/lobby", 529, true, false, 1, false, false, -1, Map.of(), Optional.empty(), 0, 1000, List.of(),
                null, NetworkCheck.Report.NOT_CHECKED, Set.of(), Set.of(), true, true, true, Map.of(), Map.of(), List.of());
        Check spawn = find(checks.setup(facts), "Spawn");
        assertEquals(Check.Status.WARN, spawn.status());
        assertEquals("/setup-lobby setspawn", spawn.command());
    }

    @Test
    void theBridgeNeverAnsweringAfterPlayersJoinedIsAFailWithTheProxySideToCheck() {
        SetupFacts facts = withBridge(false, Optional.empty(), 5);
        assertEquals(Check.Status.FAIL, find(checks.setup(facts), "Proxy bridge").status());
        Check doctor = find(checks.doctor(facts), "Proxy bridge");
        assertTrue(doctor.detail().contains("lobby-servers"), doctor.detail());

        // Before anyone joined it is only waiting.
        assertEquals(Check.Status.INFO, find(checks.setup(withBridge(false, Optional.empty(), 0)), "Proxy bridge").status());
    }

    @Test
    void viaVersionAndItsAddonsAreReported() {
        var noVia = Optional.of(new BridgeMessage.ProxyInfo("0.4.0", "Velocity 3.5.1", true, false, false, false));
        assertEquals(Check.Status.WARN, find(checks.setup(withBridge(true, noVia, 3)), "ViaVersion").status());
        assertTrue(find(checks.doctor(withBridge(true, noVia, 3)), "ViaVersion").detail().contains("ViaBackwards"));

        var noRewind = Optional.of(new BridgeMessage.ProxyInfo("0.4.0", "Velocity 3.5.1", true, true, true, false));
        assertTrue(find(checks.setup(withBridge(true, noRewind, 3)), "ViaVersion").detail().contains("1.8"));

        assertTrue(find(checks.setup(withBridge(true, Optional.empty(), 3)), "ViaVersion").detail()
                .contains("older than 0.4"), "an old bridge cannot tell");
    }

    @Test
    void aGroupNothingLeadsToGetsAnNpcSuggestion() {
        SetupFacts facts = network();
        SetupFacts missing = new SetupFacts(facts.mode(), facts.onlineMode(), facts.velocitySecretSet(), 0,
                facts.worldFormat(), facts.worldName(), facts.worldPath(), facts.chunks(), false, true, 2, true, true,
                1000, Map.of("bedwars", List.of("bw-1"), "skywars", List.of("sw-1"), "lobbies", List.of("lobby-1")),
                facts.proxy(), 12, 600_000, facts.integrations(), null, facts.network(), Set.of("bedwars"), facts.menus(),
                true, true, true, Map.of(), Map.of(), List.of());
        Check skywars = find(checks.setup(missing), "Group skywars");
        assertEquals("/npc create skywars", skywars.command());
    }

    @Test
    void theDoctorExplainsDatabaseErrors() {
        assertTrue(SetupChecks.databaseHint("Access denied for user 'lobby'@'10.0.0.5'").contains("GRANT"));
        assertTrue(SetupChecks.databaseHint("Connection refused").contains("firewall"));
        assertTrue(SetupChecks.databaseHint("Unknown database 'lobby'").contains("CREATE DATABASE"));
    }

    @Test
    void theDoctorListsBrokenEntriesAndOneEightNotes() {
        SetupFacts facts = network();
        SetupFacts broken = new SetupFacts(facts.mode(), facts.onlineMode(), true, 0, facts.worldFormat(),
                facts.worldName(), facts.worldPath(), facts.chunks(), false, true, 2, true, true, 1000, facts.groups(),
                facts.proxy(), 12, 600_000, facts.integrations(), null, facts.network(), facts.referencedGroups(),
                facts.menus(), true, true, true, Map.of(), Map.of("data/npcs.yml", List.of("guide")),
                List.of("menus.yml: ... does not exist in Minecraft 1.8, so players on old clients ...",
                        "chat.yml: option 'max-length' is missing"));
        List<Check> doctor = checks.doctor(broken);
        assertTrue(find(doctor, "data/npcs.yml").detail().contains("guide"));
        assertEquals(Check.Status.INFO, find(doctor, "1.8 clients").status());
        assertTrue(find(doctor, "Config files").detail().contains("max-length"));
    }

    @Test
    void featuresAddTheirOwnSetupSteps() {
        checks.addSetupStep(() -> List.of(Check.info("PvP", "not set up", "/pvp create ")));
        List<Check> setup = checks.setup(network());
        assertEquals("PvP", setup.getLast().title());
    }

    private static SetupFacts withBridge(boolean seen, Optional<BridgeMessage.ProxyInfo> proxy, int joined) {
        SetupFacts f = network();
        return new SetupFacts(f.mode(), f.onlineMode(), true, 0, f.worldFormat(), f.worldName(), f.worldPath(),
                f.chunks(), false, true, 2, true, seen, seen ? 1000 : -1, seen ? f.groups() : Map.of(), proxy, joined,
                600_000, f.integrations(), null, f.network(), f.referencedGroups(), f.menus(), true, true, true, Map.of(),
                Map.of(), List.of());
    }
}
