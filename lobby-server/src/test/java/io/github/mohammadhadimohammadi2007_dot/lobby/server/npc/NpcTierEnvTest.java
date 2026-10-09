package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.player.GameProfile;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each client tier is sent for an NPC: the player-list entry dance for 1.8 skins, the scale only for
 * clients that draw it, the glow colour through the NPC's team, poses and the seat of a sitting NPC.
 */
@EnvTest
class NpcTierEnvTest {

    private static final Pos NPC_AT = new Pos(0.5, 41, 0.5);
    private static final PlayerSkin SKIN = new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl");
    private static final PlayerSkin OTHER_SKIN = new PlayerSkin("b3RoZXI=", "c2lnMg==");

    @TempDir
    Path dir;

    private final List<NpcService> started = new ArrayList<>();
    private final List<ClientObjectRenderer> renderers = new ArrayList<>();
    /** Players whose client is "1.8 through ViaRewind" in these tests. */
    private final Set<UUID> legacy = ConcurrentHashMap.newKeySet();
    /** Players whose client draws the scale attribute (1.20.5+). */
    private final Set<UUID> scaled = ConcurrentHashMap.newKeySet();
    private final AtomicLong delayMillis = new AtomicLong(60_000);

    @AfterEach
    void stop() {
        started.forEach(NpcService::shutdown);
        renderers.forEach(ClientObjectRenderer::shutdown);
    }

    private record Setup(NpcService npcs, ClientObjectRenderer renderer, TeamManager teams) {
    }

    private Setup start(Env env, InstanceContainer map) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        renderer.temporaryListingDelay(delayMillis::get);
        renderers.add(renderer);
        TeamManager teams = new TeamManager(player -> 47, false);
        NpcService npcs = NpcService.start(dir, renderer,
                new Hologram.Services(text, permissions, player -> legacy.contains(player.getUuid()),
                        WorldScope.mainMap(map), player -> scaled.contains(player.getUuid())),
                new ActionServices(config, text, permissions, bridge), new NpcSkins(null, null), teams);
        started.add(npcs);
        EventNode<PlayerEvent> node = EventNode.type("npc-tier-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        teams.register(node);
        return new Setup(npcs, renderer, teams);
    }

    private NpcData npcWithSkin(Setup setup, String name, Pos at) {
        NpcData npc = setup.npcs().create(name, at);
        assertNotNull(npc);
        npc.resolvedSkin(SKIN);
        npc.skin(NpcSkin.TEXTURE);
        setup.npcs().changed(npc);
        return npc;
    }

    private static TestConnection join(Env env, InstanceContainer map, String name, Set<UUID> tier) {
        UUID id = UUID.randomUUID();
        if (tier != null) {
            tier.add(id);
        }
        TestConnection connection = env.createConnection(new GameProfile(id, name));
        connection.connect(map, NPC_AT.add(3, 0, 0));
        return connection;
    }

    /** The packets about this NPC's player-list entry and body, in the order the client got them. */
    private static List<String> entryAndSpawnOrder(List<ServerPacket> packets, UUID entry) {
        List<String> order = new ArrayList<>();
        for (ServerPacket packet : packets) {
            switch (packet) {
                case PlayerInfoUpdatePacket info -> info.entries().stream().filter(e -> e.uuid().equals(entry))
                        .forEach(e -> order.add(e.listed() ? "listed entry" : "hidden entry"));
                case SpawnEntityPacket spawn when spawn.uuid().equals(entry) -> order.add("spawn");
                case PlayerInfoRemovePacket remove when remove.uuids().contains(entry) -> order.add("remove entry");
                default -> {
                }
            }
        }
        return order;
    }

    @Test
    void legacyClientsGetAListedEntryThatIsRemovedLaterModernOnesAHiddenOne(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection modern = join(env, map, "Modern", null);
        TestConnection old = join(env, map, "Old", legacy);
        List<NpcData> npcs = List.of(npcWithSkin(setup, "bedwars", NPC_AT), npcWithSkin(setup, "skywars", NPC_AT.add(1, 0, 0)),
                npcWithSkin(setup, "duels", NPC_AT.add(-1, 0, 0)));
        delayMillis.set(150);
        Collector<ServerPacket> toModern = modern.trackIncoming(ServerPacket.class);
        Collector<ServerPacket> toOld = old.trackIncoming(ServerPacket.class);

        setup.renderer().refreshNow();
        assertEquals(3, setup.renderer().pendingTabRemovals(), "one waiting removal per NPC, for the 1.8 viewer only");
        Thread.sleep(250);
        setup.renderer().refreshNow();

        List<ServerPacket> modernPackets = toModern.collect();
        List<ServerPacket> oldPackets = toOld.collect();
        for (NpcData npc : npcs) {
            assertEquals(List.of("hidden entry", "spawn"), entryAndSpawnOrder(modernPackets, npc.profileId()),
                    "1.19.3+ clients: an entry that never shows, then the NPC");
            assertEquals(List.of("listed entry", "spawn", "remove entry"), entryAndSpawnOrder(oldPackets, npc.profileId()),
                    "1.8 clients: listed while the NPC appears, so the skin loads, then out of the tab list");
        }
        List<PlayerInfoRemovePacket> removals = oldPackets.stream()
                .filter(PlayerInfoRemovePacket.class::isInstance).map(PlayerInfoRemovePacket.class::cast).toList();
        assertEquals(1, removals.size(), "the three removals go out in one packet");
        assertEquals(Set.of(npcs.get(0).profileId(), npcs.get(1).profileId(), npcs.get(2).profileId()),
                Set.copyOf(removals.getFirst().uuids()));
        assertEquals(0, setup.renderer().pendingTabRemovals());
    }

    @Test
    void theRemovalWaitsForTheConfiguredDelay(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection old = join(env, map, "Old", legacy);
        NpcData npc = npcWithSkin(setup, "guide", NPC_AT);
        Collector<PlayerInfoRemovePacket> removals = old.trackIncoming(PlayerInfoRemovePacket.class);

        setup.renderer().refreshNow();
        setup.renderer().refreshNow();

        assertEquals(List.of(), removals.collect(), "still listed: the delay is a minute in this test");
        assertEquals(1, setup.renderer().pendingTabRemovals());
        assertNotNull(npc);
    }

    @Test
    void showInTabListsTheNpcForEveryTierAndNeverRemovesIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection modern = join(env, map, "Modern", null);
        TestConnection old = join(env, map, "Old", legacy);
        NpcData npc = npcWithSkin(setup, "guide", NPC_AT);
        npc.showInTab(true);
        setup.npcs().changed(npc);
        delayMillis.set(0);
        Collector<ServerPacket> toModern = modern.trackIncoming(ServerPacket.class);
        Collector<ServerPacket> toOld = old.trackIncoming(ServerPacket.class);

        setup.renderer().refreshNow();
        setup.renderer().refreshNow();

        assertEquals(List.of("listed entry", "spawn"), entryAndSpawnOrder(toModern.collect(), npc.profileId()));
        assertEquals(List.of("listed entry", "spawn"), entryAndSpawnOrder(toOld.collect(), npc.profileId()));
        assertEquals(0, setup.renderer().pendingTabRemovals());
    }

    @Test
    void aNewSkinRemovesTheOldEntryBeforeAddingTheNewOne(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection modern = join(env, map, "Modern", null);
        NpcData npc = npcWithSkin(setup, "guide", NPC_AT);
        setup.renderer().refreshNow();
        Collector<ServerPacket> packets = modern.trackIncoming(ServerPacket.class);

        npc.resolvedSkin(OTHER_SKIN);
        setup.npcs().changed(npc);
        setup.renderer().refreshNow();

        // A client keeps the first entry it has for a UUID, so without the removal the new skin never shows.
        assertEquals(List.of("remove entry", "hidden entry", "spawn"), entryAndSpawnOrder(packets.collect(), npc.profileId()));
    }

    @Test
    void theScaleIsOnlySentToClientsThatDrawIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection newer = join(env, map, "Newer", scaled);
        TestConnection older = join(env, map, "Older", null);
        NpcData npc = setup.npcs().create("giant", NPC_AT);
        assertNotNull(npc);
        npc.scale(2);
        setup.npcs().changed(npc);
        Collector<ServerPacket> toNewer = newer.trackIncoming(ServerPacket.class);
        Collector<ServerPacket> toOlder = older.trackIncoming(ServerPacket.class);

        setup.renderer().refreshNow();

        List<ServerPacket> newerPackets = toNewer.collect();
        int body = bodyId(newerPackets, npc);
        EntityAttributesPacket attributes = newerPackets.stream().filter(EntityAttributesPacket.class::isInstance)
                .map(EntityAttributesPacket.class::cast).filter(p -> p.entityId() == body).findFirst().orElseThrow();
        assertEquals(Attribute.SCALE, attributes.properties().getFirst().attribute());
        assertEquals(2, attributes.properties().getFirst().value());
        List<ServerPacket> olderPackets = toOlder.collect();
        assertFalse(olderPackets.stream().anyMatch(EntityAttributesPacket.class::isInstance),
                "a client before 1.20.5 has no scale attribute");
        // Each sees the name right above the NPC as their client draws it.
        assertEquals(41 + 1.8 * 2 + NpcData.NAME_GAP, nameY(newerPackets), 1e-6);
        assertEquals(41 + 1.8 + NpcData.NAME_GAP, nameY(olderPackets), 1e-6);
    }

    @Test
    void theGlowColourIsTheColourOfTheNpcsTeam(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        NpcData npc = setup.npcs().create("guide", NPC_AT);
        NpcData trader = setup.npcs().create("trader", NPC_AT);
        assertNotNull(npc);
        assertNotNull(trader);
        npc.glowing(true);
        npc.glowColor(TeamColor.RED);
        setup.npcs().changed(npc);
        trader.type(EntityType.VILLAGER);
        trader.glowColor(TeamColor.RED);
        setup.npcs().changed(trader);
        TestConnection viewer = env.createConnection(new GameProfile(UUID.randomUUID(), "Viewer"));
        Collector<TeamsPacket> teams = viewer.trackIncoming(TeamsPacket.class);
        viewer.connect(map, NPC_AT);

        TeamsPacket red = teams.collect().stream().filter(p -> p.teamName().equals("zzhred")).findFirst().orElseThrow();
        TeamsPacket.CreateTeamAction create = (TeamsPacket.CreateTeamAction) red.action();
        assertEquals(TeamColor.RED, create.settings().color());
        assertEquals(TeamsPacket.NameTagVisibility.NEVER, create.settings().nameTagVisibility());
        // A player NPC is in its team by profile name, any other entity by its UUID.
        assertTrue(create.entities().contains(npc.profileName()), create.entities().toString());
        assertTrue(create.entities().contains(trader.profileId().toString()), create.entities().toString());

        Collector<TeamsPacket> changes = viewer.trackIncoming(TeamsPacket.class);
        npc.glowColor(TeamColor.BLUE);
        setup.npcs().changed(npc);
        List<TeamsPacket> sent = changes.collect();
        assertEquals("zzhred", sent.get(0).teamName(), "it leaves the red team first");
        assertTrue(sent.get(0).action() instanceof TeamsPacket.RemoveEntitiesToTeamAction);
        assertEquals("zzhblue", sent.get(1).teamName());
        assertEquals("zzhblue", setup.teams().hiddenTeamOf(npc.profileName()));
    }

    @Test
    void aMobNpcIsKnownByItsOwnUuidSoItsTeamFindsIt(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection viewer = join(env, map, "Viewer", null);
        NpcData trader = setup.npcs().create("trader", NPC_AT);
        assertNotNull(trader);
        trader.type(EntityType.VILLAGER);
        setup.npcs().changed(trader);
        Collector<SpawnEntityPacket> spawns = viewer.trackIncoming(SpawnEntityPacket.class);

        setup.renderer().refreshNow();

        SpawnEntityPacket villager = spawns.collect().stream().filter(p -> p.type().equals(EntityType.VILLAGER))
                .findFirst().orElseThrow();
        assertEquals(trader.profileId(), villager.uuid());
    }

    @Test
    void posesAreSentAndASittingNpcRidesAnInvisibleSeat(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection viewer = join(env, map, "Viewer", null);
        NpcData crouching = setup.npcs().create("crouching", NPC_AT);
        NpcData sitting = setup.npcs().create("sitting", NPC_AT.add(2, 0, 0));
        assertNotNull(crouching);
        assertNotNull(sitting);
        assertTrue(crouching.pose(NpcPose.CROUCHING));
        assertTrue(sitting.pose(NpcPose.SITTING));
        setup.npcs().changed(crouching);
        setup.npcs().changed(sitting);
        Collector<ServerPacket> packets = viewer.trackIncoming(ServerPacket.class);

        setup.renderer().refreshNow();

        List<ServerPacket> all = packets.collect();
        int crouchingBody = bodyId(all, crouching);
        EntityMetaDataPacket metadata = all.stream().filter(EntityMetaDataPacket.class::isInstance)
                .map(EntityMetaDataPacket.class::cast).filter(p -> p.entityId() == crouchingBody).findFirst().orElseThrow();
        assertEquals(EntityPose.SNEAKING, metadata.entries().get(MetadataDef.POSE.index()).value());

        int sittingBody = bodyId(all, sitting);
        int ride = indexOf(all, SetPassengersPacket.class);
        SetPassengersPacket passengers = (SetPassengersPacket) all.get(ride);
        assertEquals(List.of(sittingBody), passengers.passengersId());
        int seatSpawn = -1;
        int bodySpawn = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) instanceof SpawnEntityPacket spawn && spawn.entityId() == passengers.vehicleEntityId()) {
                seatSpawn = i;
                assertEquals(EntityType.TEXT_DISPLAY, spawn.type(), "an empty text display, as FancyNpcs uses");
            }
            if (all.get(i) instanceof SpawnEntityPacket spawn && spawn.entityId() == sittingBody) {
                bodySpawn = i;
            }
        }
        assertTrue(seatSpawn >= 0 && bodySpawn >= 0 && ride > seatSpawn && ride > bodySpawn,
                "both ends of the ride exist before it is sent");
    }

    @Test
    void onlyPlayersHavePoses() {
        NpcData villager = new NpcData("trader", NPC_AT, List.of());
        villager.type(EntityType.VILLAGER);
        assertFalse(villager.pose(NpcPose.SLEEPING));
        assertEquals(NpcPose.STANDING, villager.pose());
        NpcData player = new NpcData("guide", NPC_AT, List.of());
        assertTrue(player.pose(NpcPose.SLEEPING));
        assertEquals(41 + 0.2 + NpcData.NAME_GAP, player.nameTag().position().y(), 1e-9,
                "the name follows the pose down");
    }

    private static int bodyId(List<ServerPacket> packets, NpcData npc) {
        return packets.stream().filter(SpawnEntityPacket.class::isInstance).map(SpawnEntityPacket.class::cast)
                .filter(p -> p.uuid().equals(npc.profileId())).findFirst().orElseThrow().entityId();
    }

    private static double nameY(List<ServerPacket> packets) {
        return packets.stream().filter(SpawnEntityPacket.class::isInstance).map(SpawnEntityPacket.class::cast)
                .filter(p -> p.type().equals(EntityType.TEXT_DISPLAY)).findFirst().orElseThrow().position().y();
    }

    private static int indexOf(List<ServerPacket> packets, Class<?> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " was sent");
    }
}
