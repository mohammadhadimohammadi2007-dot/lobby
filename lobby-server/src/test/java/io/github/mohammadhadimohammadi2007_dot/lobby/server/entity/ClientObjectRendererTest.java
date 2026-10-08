package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the rule the whole display layer rests on: viewers who would see the same thing share one set of
 * entities and one render, and only what changed is sent.
 *
 * <p>Note: a Minestom test collector stops collecting when {@code collect()} is called, so each phase takes
 * a fresh one. Players are spawned with the same packet as entities, so hologram spawns are filtered by type.
 */
@EnvTest
class ClientObjectRendererTest {

    private static final Pos ORIGIN = new Pos(0, 41, 0);

    /** A one-line text hologram; {@code perPlayer} makes its text differ per viewer. */
    private static final class TextObject implements ClientObject {
        private final AtomicReference<String> text;
        private final boolean perPlayer;
        private final AtomicInteger renders = new AtomicInteger();
        private final Set<String> renderThreads = ConcurrentHashMap.newKeySet();
        private volatile double distance = 32;
        private volatile WorldScope scope = WorldScope.anywhere();

        TextObject(String text, boolean perPlayer) {
            this.text = new AtomicReference<>(text);
            this.perPlayer = perPlayer;
        }

        @Override
        public String name() {
            return "greeting";
        }

        @Override
        public Pos position() {
            return ORIGIN;
        }

        @Override
        public WorldScope scope() {
            return scope;
        }

        @Override
        public double viewDistance() {
            return distance;
        }

        @Override
        public Object variantKey(Player viewer) {
            return perPlayer ? viewer.getUsername() : "all";
        }

        @Override
        public List<EntityPart> render(Object key, Player viewer) {
            renders.incrementAndGet();
            renderThreads.add(Thread.currentThread().getName());
            String shown = perPlayer ? text.get() + " " + viewer.getUsername() : text.get();
            return List.of(new EntityPart(EntityType.TEXT_DISPLAY, ORIGIN,
                    Map.of(MetadataDef.TextDisplay.TEXT.index(), Metadata.Component(Component.text(shown)))));
        }
    }

    private Player join(Env env, Instance instance, String name) {
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), name));
        return connection.connect(instance, ORIGIN);
    }

    /** Only the spawns of our own entities, not of other players. */
    private static List<SpawnEntityPacket> hologramSpawns(Collector<SpawnEntityPacket> collector) {
        return collector.collect().stream()
                .filter(packet -> packet.type().equals(EntityType.TEXT_DISPLAY))
                .toList();
    }

    @Test
    void sharedContentIsBuiltOnceForEveryone(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        Instance instance = env.createFlatInstance();
        Instance second = env.createFlatInstance(); // Another instance: client entities are per viewer.
        TestConnection first = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        TestConnection other = env.createConnection(new GameProfile(UUID.randomUUID(), "Bob"));
        first.connect(instance, ORIGIN);
        other.connect(second, ORIGIN);
        TextObject hologram = new TextObject("Welcome", false);
        renderer.put(hologram);
        var annSpawns = first.trackIncoming(SpawnEntityPacket.class);
        var bobSpawns = other.trackIncoming(SpawnEntityPacket.class);

        renderer.refreshNow();

        assertEquals(1, hologram.renders.get());
        assertEquals(1, renderer.renderCount());
        List<SpawnEntityPacket> forAnn = annSpawns.collect().stream()
                .filter(packet -> packet.type().equals(EntityType.TEXT_DISPLAY)).toList();
        List<SpawnEntityPacket> forBob = bobSpawns.collect().stream()
                .filter(packet -> packet.type().equals(EntityType.TEXT_DISPLAY)).toList();
        assertEquals(1, forAnn.size());
        assertEquals(1, forBob.size());
        assertEquals(forAnn.getFirst().entityId(), forBob.getFirst().entityId(), "viewers share one entity");
        assertEquals("greeting", renderer.objectOf(forAnn.getFirst().entityId()));
        assertNull(renderer.objectOf(forAnn.getFirst().entityId() + 10_000));

        // Nothing changed: no second render and no packets.
        var quiet = first.trackIncoming(EntityMetaDataPacket.class);
        renderer.refreshNow();
        assertEquals(1, hologram.renders.get());
        assertEquals(List.of(), quiet.collect());
        renderer.shutdown();
    }

    @Test
    void nothingIsBuiltOnTheTickThread(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer(1);
        EventNode<PlayerEvent> node = EventNode.type("display-thread-test", EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        renderer.register(node);
        Instance instance = env.createFlatInstance();
        join(env, instance, "Ann");
        TextObject hologram = new TextObject("Welcome", true);
        renderer.put(hologram);
        String tickThread = Thread.currentThread().getName();

        // Both ways of refreshing: the scheduled one and the one tests use.
        renderer.refreshNow();
        env.tickWhile(() -> hologram.renders.get() < 2, Duration.ofSeconds(5));

        assertEquals(Set.of("lobby-display"), hologram.renderThreads,
                "holograms are built on their own thread, never on the tick thread (" + tickThread + ")");
        renderer.shutdown();
    }

    @Test
    void anotherWorldDoesNotShowTheObject(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        InstanceContainer map = (InstanceContainer) env.createFlatInstance();
        Instance secondLobby = MinecraftServer.getInstanceManager().createSharedInstance(map);
        InstanceContainer otherWorld = (InstanceContainer) env.createFlatInstance();
        TestConnection inLobby = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        TestConnection inSecondLobby = env.createConnection(new GameProfile(UUID.randomUUID(), "Bob"));
        TestConnection elsewhere = env.createConnection(new GameProfile(UUID.randomUUID(), "Cid"));
        inLobby.connect(map, ORIGIN);
        inSecondLobby.connect(secondLobby, ORIGIN);
        // Same coordinates, different world: the parkour map of phase 4 must not show lobby holograms.
        elsewhere.connect(otherWorld, ORIGIN);
        TextObject hologram = new TextObject("Welcome", false);
        hologram.scope = WorldScope.mainMap(map);
        renderer.put(hologram);
        var lobbySpawns = inLobby.trackIncoming(SpawnEntityPacket.class);
        var secondLobbySpawns = inSecondLobby.trackIncoming(SpawnEntityPacket.class);
        var elsewhereSpawns = elsewhere.trackIncoming(SpawnEntityPacket.class);

        renderer.refreshNow();

        assertEquals(1, hologramSpawns(lobbySpawns).size());
        assertEquals(1, hologramSpawns(secondLobbySpawns).size(), "every lobby instance of the map shows it");
        assertEquals(List.of(), hologramSpawns(elsewhereSpawns), "another world does not");

        // Only one render, although the viewers are in two different instances of the map.
        assertEquals(1, hologram.renders.get());

        // Scoped to one lobby instead, the other lobby loses it again.
        var despawn = inSecondLobby.trackIncoming(DestroyEntitiesPacket.class);
        hologram.scope = WorldScope.only(map);
        renderer.refreshNow();
        assertEquals(1, despawn.collect().size());
        renderer.shutdown();
    }

    @Test
    void perPlayerContentGetsItsOwnEntities(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        Instance instance = env.createFlatInstance();
        TestConnection first = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        TestConnection other = env.createConnection(new GameProfile(UUID.randomUUID(), "Bob"));
        first.connect(instance, ORIGIN);
        other.connect(instance, ORIGIN);
        TextObject hologram = new TextObject("Hello", true);
        renderer.put(hologram);
        var annSpawns = first.trackIncoming(SpawnEntityPacket.class);
        var bobSpawns = other.trackIncoming(SpawnEntityPacket.class);

        renderer.refreshNow();

        assertEquals(2, hologram.renders.get());
        int ann = annSpawns.collect().stream().filter(p -> p.type().equals(EntityType.TEXT_DISPLAY))
                .findFirst().orElseThrow().entityId();
        int bob = bobSpawns.collect().stream().filter(p -> p.type().equals(EntityType.TEXT_DISPLAY))
                .findFirst().orElseThrow().entityId();
        assertTrue(ann != bob, "different text needs different entities");
        renderer.shutdown();
    }

    @Test
    void changedTextSendsOnlyMetadata(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        connection.connect(env.createFlatInstance(), ORIGIN);
        TextObject hologram = new TextObject("Welcome", false);
        renderer.put(hologram);
        renderer.refreshNow();

        hologram.text.set("Changed");
        renderer.invalidate("greeting");
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        var updates = connection.trackIncoming(EntityMetaDataPacket.class);
        renderer.refreshNow();

        assertEquals(List.of(), spawns.collect(), "a text change must not respawn the entity");
        List<EntityMetaDataPacket> sent = updates.collect();
        assertEquals(1, sent.size());
        assertEquals(1, sent.getFirst().entries().size(), "only the changed entry is sent");
        renderer.shutdown();
    }

    @Test
    void walkingAwayDespawnsAndComingBackSpawnsAgain(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        connection.connect(env.createFlatInstance(), ORIGIN.add(10, 0, 0));
        TextObject hologram = new TextObject("Welcome", false);
        renderer.put(hologram);
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        renderer.refreshNow();
        int entityId = hologramSpawns(spawns).getFirst().entityId();

        hologram.distance = 4;
        var destroys = connection.trackIncoming(DestroyEntitiesPacket.class);
        renderer.refreshNow();
        assertEquals(List.of(List.of(entityId)), destroys.collect().stream()
                .map(DestroyEntitiesPacket::entityIds).toList());

        hologram.distance = 32;
        var backAgain = connection.trackIncoming(SpawnEntityPacket.class);
        renderer.refreshNow();
        assertEquals(1, hologramSpawns(backAgain).size(), "coming back spawns it again");
        renderer.shutdown();
    }

    @Test
    void removingDespawnsForEveryone(Env env) {
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        TestConnection connection = env.createConnection(new GameProfile(UUID.randomUUID(), "Ann"));
        connection.connect(env.createFlatInstance(), ORIGIN);
        renderer.put(new TextObject("Welcome", false));
        var spawns = connection.trackIncoming(SpawnEntityPacket.class);
        renderer.refreshNow();
        int entityId = hologramSpawns(spawns).getFirst().entityId();

        var destroys = connection.trackIncoming(DestroyEntitiesPacket.class);
        renderer.remove("greeting");
        renderer.refreshNow();

        assertEquals(List.of(List.of(entityId)), destroys.collect().stream()
                .map(DestroyEntitiesPacket::entityIds).toList());
        assertNull(renderer.objectOf(entityId));
        renderer.shutdown();
    }
}
