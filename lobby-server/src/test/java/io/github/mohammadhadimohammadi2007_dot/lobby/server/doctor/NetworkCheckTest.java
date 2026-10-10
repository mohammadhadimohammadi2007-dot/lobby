package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.npc.NpcTrigger;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.portal.Portal;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The check of group and server names against what the proxy bridge reports. */
class NetworkCheckTest {

    @TempDir
    Path dir;

    private static NetworkState network(Map<String, List<String>> groups) {
        NetworkState network = new NetworkState();
        network.update(new BridgeMessage.NetworkSnapshot(0, Map.of(), groups));
        return network;
    }

    @Test
    void theBundledMenusNamesAreReportedWhenTheProxyCallsItsGroupsDifferently() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        NetworkState network = network(Map.of("bw", List.of("bw-1"), "lobbies", List.of("lobby")));
        NpcData npc = new NpcData("guide", Pos.ZERO, List.of("%group_online_skywars% playing"));
        npc.actions(NpcTrigger.ANY_CLICK, new NpcData.Actions(List.of("connect: sw-1"),
                ActionParser.parseList(List.of("connect: sw-1"), 0, "test", warning -> { })));
        Portal portal = Portal.create("gate", new Region(Portal.OWNER, "gate", 0, 0, 0, 1, 1, 1))
                .withActions(List.of("connect_group: bw"), warning -> { });

        NetworkCheck.Report report = NetworkCheck.check(
                NetworkReferences.collect(config.current(), List.of(), List.of(npc), List.of(portal)), network);

        assertTrue(report.checked());
        assertFalse(report.ok());
        List<String> unknown = report.unknown().stream().map(u -> u.kind().word() + " " + u.name()).toList();
        assertTrue(unknown.containsAll(List.of("group bedwars", "group skywars", "group duels", "server sw-1")), unknown.toString());
        assertFalse(unknown.contains("group bw"), "the portal's group exists: " + unknown);
        NetworkCheck.Unknown bedwars = report.unknown().stream().filter(u -> u.name().equals("bedwars")).findFirst()
                .orElseThrow();
        assertTrue(bedwars.places().stream().anyMatch(place -> place.startsWith("menus.yml: servers")), bedwars.places().toString());
        NetworkCheck.Unknown skywars = report.unknown().stream().filter(u -> u.name().equals("skywars")).findFirst()
                .orElseThrow();
        assertTrue(skywars.places().contains("NPC guide (name)"), skywars.places().toString());
        String warning = report.warning();
        assertTrue(warning.contains("The proxy has the groups: bw, lobbies"), warning);
        assertTrue(report.summary().contains("group 'bedwars'"), report.summary());
    }

    @Test
    void namesThatArePlaceholdersAreLeftOut() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        NpcData npc = new NpcData("guide", Pos.ZERO, List.of("%group_online_%player_game%% and %bungee_total%"));
        Portal portal = Portal.create("p", new Region(Portal.OWNER, "p", 0, 0, 0, 0, 0, 0))
                .withActions(List.of("connect: %player_last_server%"), warning -> { });

        List<NetworkReference> found = NetworkReferences.collect(config.current(), List.of(), List.of(npc),
                List.of(portal));

        assertEquals(List.of(), found.stream().filter(reference -> reference.where().startsWith("NPC")
                || reference.where().startsWith("portal")).toList(), "only known when the action runs");
    }

    @Test
    void nothingIsCheckedBeforeTheBridgeSaysAnything() {
        NetworkCheck.Report report = NetworkCheck.check(List.of(
                new NetworkReference(NetworkReference.Kind.GROUP, "bedwars", "x")), new NetworkState());
        assertFalse(report.checked());
        assertTrue(report.summary().startsWith("not checked yet"));
    }

    @Test
    void theServiceRunsWhenTheFirstSnapshotArrivesAndWhenNamesChange() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        NetworkState network = new NetworkState();
        AtomicInteger runs = new AtomicInteger();
        NetworkCheckService service = new NetworkCheckService(config, network, new NetworkCheckService.Sources(
                List::of, List::of, () -> {
                    runs.incrementAndGet();
                    return List.of();
                }));
        service.start();

        network.update(new BridgeMessage.NetworkSnapshot(5, Map.of("bedwars-1", 5), Map.of("bedwars", List.of("bedwars-1"))));
        network.update(new BridgeMessage.NetworkSnapshot(6, Map.of("bedwars-1", 6), Map.of("bedwars", List.of("bedwars-1"))));
        assertEquals(1, runs.get(), "player counts change every second; the names did not");
        network.update(new BridgeMessage.NetworkSnapshot(6, Map.of("bedwars-1", 6), Map.of("bedwars", List.of("bedwars-1"),
                "skywars", List.of())));
        assertEquals(2, runs.get(), "a new group: checked again");
        assertTrue(service.last().checked());
        assertTrue(service.last().unknown().stream().noneMatch(u -> u.name().equals("bedwars")));
    }
}
