package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectClicks;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.IntegrationStatus;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin.BuiltinPlaceholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.LobbyWorld;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldFormat;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPCs on a running server: what a client is sent, turning, clicks and how often names are built. */
@EnvTest
class NpcEnvTest {

    private static final Pos NPC_AT = new Pos(0.5, 41, 0.5);
    private static final Pos LEFT_TARGET = new Pos(100, 41, 100);
    private static final Pos RIGHT_TARGET = new Pos(-100, 41, -100);
    private static final PlayerSkin SKIN = new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl");

    @TempDir
    Path dir;

    private final List<NpcService> started = new ArrayList<>();
    private final List<ClientObjectRenderer> renderers = new ArrayList<>();

    @AfterEach
    void stop() {
        started.forEach(NpcService::shutdown);
        renderers.forEach(ClientObjectRenderer::shutdown);
    }

    /** A running NPC service with its renderer, teams and clicks. */
    private record Setup(NpcService npcs, ClientObjectRenderer renderer) {
    }

    private Setup start(Env env, InstanceContainer map) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        PlaceholderService placeholders = new PlaceholderService(new PlaceholderRegistry());
        BuiltinPlaceholders.registerAll(placeholders.registry(), new BuiltinPlaceholders.Sources(
                config, permissions, MuteService.NONE, bridge, info(map), LobbyInstanceInfo.SINGLE));
        LobbyText text = new LobbyText(config, placeholders);
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        renderers.add(renderer);
        TeamManager teams = new TeamManager(player -> bridge.capabilities(player).protocolVersion(), false);
        // No Mojang and no SkinsRestorer: tests never go to the internet.
        NpcService npcs = NpcService.start(dir, renderer,
                new Hologram.Services(text, permissions, player -> false, WorldScope.mainMap(map)),
                new ActionServices(config, text, permissions, bridge), new NpcSkins(null, null), teams);
        started.add(npcs);
        ClientObjectClicks clicks = new ClientObjectClicks(renderer);
        clicks.addHandler(npcs);
        EventNode<PlayerEvent> node = EventNode.type("npc-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        clicks.register(node);
        teams.register(node);
        return new Setup(npcs, renderer);
    }

    private static List<SpawnEntityPacket> spawnsOf(Collector<SpawnEntityPacket> collector, EntityType type) {
        return collector.collect().stream().filter(packet -> packet.type().equals(type)).toList();
    }

    @Test
    void aPlayerNpcIsSentWithAnUnlistedProfileAndItsNameTag(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        connection.connect(map, NPC_AT.add(3, 0, 0));
        NpcData npc = setup.npcs().create("guide", NPC_AT);
        assertNotNull(npc);
        npc.resolvedSkin(SKIN);
        npc.skin(NpcSkin.TEXTURE);
        setup.npcs().changed(npc);
        var info = connection.trackIncoming(PlayerInfoUpdatePacket.class);
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);

        setup.renderer().refreshNow();

        List<SpawnEntityPacket> allSpawns = spawns.collect();
        // Ours, not the other players: the renderer knows which entity ids belong to an object.
        SpawnEntityPacket body = allSpawns.stream().filter(p -> p.type().equals(EntityType.PLAYER))
                .filter(p -> setup.renderer().objectOf(p.entityId()) != null).findFirst().orElseThrow();
        PlayerInfoUpdatePacket.Entry entry = info.collect().stream()
                .flatMap(packet -> packet.entries().stream())
                .filter(e -> e.uuid().equals(body.uuid())).findFirst().orElseThrow();
        assertEquals(npc.profileName(), entry.username());
        assertFalse(entry.listed(), "an NPC must never show up in the tab list");
        assertEquals(SKIN.textures(), entry.properties().getFirst().value());
        assertEquals(SKIN.signature(), entry.properties().getFirst().signature());
        assertEquals(1, allSpawns.stream().filter(p -> p.type().equals(EntityType.TEXT_DISPLAY)).count(),
                "the name tag is a text display above the NPC");
    }

    @Test
    void theProfileNameIsHiddenAboveTheHead(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        NpcData npc = setup.npcs().create("guide", NPC_AT);
        assertNotNull(npc);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        var teams = connection.trackIncoming(TeamsPacket.class);

        connection.connect(map, NPC_AT);

        // A player who joins after the NPC was made still gets the hidden-name team with it.
        boolean hidden = teams.collect().stream()
                .filter(packet -> packet.action() instanceof TeamsPacket.CreateTeamAction)
                .anyMatch(packet -> ((TeamsPacket.CreateTeamAction) packet.action()).entities()
                        .contains(npc.profileName()));
        assertTrue(hidden, "the profile name must be in the team whose names are never shown");
    }

    @Test
    void aMobNpcHasNoProfile(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        connection.connect(map, NPC_AT.add(3, 0, 0));
        NpcData npc = setup.npcs().create("trader", NPC_AT);
        assertNotNull(npc);
        npc.type(EntityType.VILLAGER);
        setup.npcs().changed(npc);
        var info = connection.trackIncoming(PlayerInfoUpdatePacket.class);
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);

        setup.renderer().refreshNow();

        assertEquals(1, spawnsOf(spawns, EntityType.VILLAGER).size());
        assertEquals(List.of(), info.collect(), "only a player-shaped NPC needs a player-list entry");
    }

    @Test
    void itTurnsTowardsANearPlayerAndOnlySaysSoWhenItChanges(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(map, NPC_AT.add(3, 0, 0));
        NpcData npc = setup.npcs().create("guide", NPC_AT);
        assertNotNull(npc);
        npc.turnToPlayer(true);
        setup.npcs().changed(npc);
        var rotations = connection.trackIncoming(EntityRotationPacket.class);
        var heads = connection.trackIncoming(EntityHeadLookPacket.class);

        setup.renderer().refreshNow();

        List<EntityRotationPacket> turned = rotations.collect();
        assertEquals(1, turned.size());
        assertEquals(1, heads.collect().size());
        // Steve stands at +x, so the NPC faces +x: yaw -90 in Minecraft's angles.
        assertEquals(-90f, turned.getFirst().yaw(), 2f);

        var quiet = connection.trackIncoming(EntityRotationPacket.class);
        setup.renderer().refreshNow();
        assertEquals(List.of(), quiet.collect(), "nothing changed, so nothing is sent");

        steve.teleport(NPC_AT.add(0, 0, 3)).join();
        var turnedAgain = connection.trackIncoming(EntityRotationPacket.class);
        setup.renderer().refreshNow();
        List<EntityRotationPacket> again = turnedAgain.collect();
        assertEquals(1, again.size());
        assertEquals(0f, again.getFirst().yaw(), 2f);
    }

    @Test
    void eachButtonRunsItsOwnListAndTheOffHandNothing(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve"));
        Player steve = connection.connect(map, NPC_AT.add(2, 0, 0));
        NpcData npc = setup.npcs().create("guide", NPC_AT);
        assertNotNull(npc);
        setup.npcs().setActions(npc, NpcTrigger.LEFT_CLICK, List.of("teleport: 100 41 100"));
        setup.npcs().setActions(npc, NpcTrigger.RIGHT_CLICK, List.of("teleport: -100 41 -100"));
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        setup.renderer().refreshNow();
        List<SpawnEntityPacket> ours = spawns.collect().stream()
                .filter(p -> setup.renderer().objectOf(p.entityId()) != null).toList();
        int body = ours.stream().filter(p -> p.type().equals(EntityType.PLAYER)).findFirst().orElseThrow().entityId();
        int nameTag = ours.stream().filter(p -> p.type().equals(EntityType.TEXT_DISPLAY)).findFirst().orElseThrow()
                .entityId();

        send(env, steve, new ClientInteractEntityPacket(body, PlayerHand.OFF, Vec.ZERO, false));
        env.tickWhile(() -> steve.getPosition().x() < LEFT_TARGET.x(), Duration.ofMillis(300));
        assertEquals(NPC_AT.x() + 2, steve.getPosition().x(), "the off hand does nothing");

        send(env, steve, new ClientInteractEntityPacket(body, PlayerHand.MAIN, Vec.ZERO, false));
        assertTrue(env.tickWhile(() -> steve.getPosition().x() > RIGHT_TARGET.x(), Duration.ofSeconds(5)));
        assertEquals(RIGHT_TARGET.x(), steve.getPosition().x(), "a right click runs the right_click list");

        // A left click on the name tag counts as a click on the NPC.
        steve.teleport(NPC_AT.add(2, 0, 0)).join();
        Thread.sleep(200); // The click rate limit.
        send(env, steve, new ClientAttackPacket(nameTag));
        assertTrue(env.tickWhile(() -> steve.getPosition().x() < LEFT_TARGET.x(), Duration.ofSeconds(5)));
        assertEquals(LEFT_TARGET.x(), steve.getPosition().x(), "a left click runs the left_click list");
    }

    @Test
    void aMirroredSkinIsBuiltForEachViewer(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        TestConnection first = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        TestConnection second = env.createConnection(new GameProfile(UUID.randomUUID(), "Bob"));
        first.connect(map, NPC_AT.add(2, 0, 0));
        second.connect(map, NPC_AT.add(-2, 0, 0));
        NpcData npc = setup.npcs().create("mirror", NPC_AT);
        assertNotNull(npc);
        CompletableFuture<NpcSkins.Result> set = setup.npcs().setSkin(npc, NpcSkin.MIRROR);
        set.join();
        var annSpawns = first.trackIncoming(SpawnEntityPacket.class);
        var bobSpawns = second.trackIncoming(SpawnEntityPacket.class);
        int before = setup.renderer().renderCount();

        setup.renderer().refreshNow();

        assertEquals(2, setup.renderer().renderCount() - before, "each viewer sees their own skin");
        int ann = spawnsOf(annSpawns, EntityType.PLAYER).stream().map(SpawnEntityPacket::entityId)
                .filter(id -> setup.renderer().objectOf(id) != null).findFirst().orElseThrow();
        int bob = spawnsOf(bobSpawns, EntityType.PLAYER).stream().map(SpawnEntityPacket::entityId)
                .filter(id -> setup.renderer().objectOf(id) != null).findFirst().orElseThrow();
        assertNotEquals(ann, bob);
    }

    /** Note 1 of the review: an NPC's name follows exactly the hologram rules, at full lobby size. */
    @Test
    void aGlobalNameIsBuiltOnceForTwoHundredPlayers(Env env) throws Exception {
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Setup setup = start(env, map);
        int viewers = 200;
        for (int number = 0; number < viewers; number++) {
            env.createConnection(new GameProfile(UUID.randomUUID(), "p" + number)).connect(map, NPC_AT.add(2, 0, 0));
        }
        NpcData bedwars = setup.npcs().create("bedwars", NPC_AT);
        assertNotNull(bedwars);
        bedwars.nameTag().frames(List.of(List.of("<gold>BedWars", "%group_online_bedwars% playing",
                "<yellow>Click to join")));
        setup.npcs().changed(bedwars);
        assertEquals(PlaceholderScope.GLOBAL, bedwars.nameTag().textScope());

        int before = setup.renderer().renderCount();
        setup.renderer().refreshNow();
        int globalRenders = setup.renderer().renderCount() - before;

        NpcData greeter = setup.npcs().create("greeter", NPC_AT.add(0, 0, 4));
        assertNotNull(greeter);
        greeter.nameTag().frames(List.of(List.of("Hello %player_name%")));
        setup.npcs().changed(greeter);
        before = setup.renderer().renderCount();
        setup.renderer().refreshNow();
        int perPlayerRenders = setup.renderer().renderCount() - before;

        System.out.println("NPC with a global name, " + viewers + " viewers: " + globalRenders
                + " render(s); NPC with %player_name%: " + perPlayerRenders + " render(s)");
        assertEquals(1, globalRenders, "a player count in an NPC's name is the same for everyone");
        assertEquals(viewers, perPlayerRenders, "a player's own name is not");
    }

    private static void send(Env env, Player player, ClientPacket packet) {
        env.process().eventHandler().call(new PlayerPacketEvent(player, packet));
    }

    private static ServerInfo info(InstanceContainer map) {
        LobbyWorld world = new LobbyWorld(map, "test-map", WorldFormat.POLAR, 1, 0, 0, false);
        return new ServerInfo() {
            @Override
            public String modeDescription() {
                return "standalone";
            }

            @Override
            public double tps() {
                return 20;
            }

            @Override
            public double mspt() {
                return 1;
            }

            @Override
            public LobbyWorld world() {
                return world;
            }

            @Override
            public List<IntegrationStatus> integrations() {
                return List.of();
            }

            @Override
            public String bridgeStatus() {
                return "off";
            }
        };
    }
}
