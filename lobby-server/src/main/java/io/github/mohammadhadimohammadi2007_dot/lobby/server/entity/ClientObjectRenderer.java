package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.PacketSendingUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Shows {@link ClientObject}s (holograms, NPCs) to the players near them.
 *
 * <p>Everything here is packets, so one object automatically exists in every lobby instance of its
 * {@link WorldScope} and costs no tick time. Viewers who would see exactly the same thing share one set of entity ids, so a hologram
 * without player placeholders is built once however many players read it. Viewers who walk out of range
 * get a destroy packet and a fresh spawn when they come back.
 *
 * <p>All of it runs on one background thread, so building text never slows the server tick.
 */
public final class ClientObjectRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientObjectRenderer.class);
    private static final int DEFAULT_REFRESH_TICKS = 5;
    /** How long a {@link EntityPart.Listing#TEMPORARY} entry stays in the tab list, unless configured. */
    public static final long DEFAULT_TEMPORARY_LISTING_MILLIS = 3000;

    /** One group of viewers that share entity ids because they see the same thing. */
    private static final class Variant {
        private final List<Integer> entityIds = new ArrayList<>();
        private final List<UUID> uuids = new ArrayList<>();
        private final Map<UUID, Player> viewers = new HashMap<>();
        private List<EntityPart> parts = List.of();

        void ensureIds(int count) {
            while (entityIds.size() < count) {
                entityIds.add(Entity.generateId());
                uuids.add(UUID.randomUUID());
            }
        }

        /** The UUID clients know part {@code index} of {@code of} by: its own if it has one, else the one picked here. */
        UUID uuidOf(int index, List<EntityPart> of) {
            UUID own = index < of.size() ? of.get(index).uuid() : null;
            return own != null ? own : uuids.get(index);
        }
    }

    /** What the renderer remembers about one object. */
    private static final class Tracked {
        private final ClientObject object;
        private final Map<Object, Variant> variants = new LinkedHashMap<>();
        private final Map<UUID, Object> viewerVariants = new HashMap<>();
        private long nextContentUpdate;
        private boolean contentDirty = true;

        Tracked(ClientObject object) {
            this.object = object;
        }
    }

    private final Map<String, Tracked> tracked = new LinkedHashMap<>();
    private final Map<Integer, String> objectByEntityId = new ConcurrentHashMap<>();
    private final AtomicInteger refreshes = new AtomicInteger();
    private final AtomicInteger renders = new AtomicInteger();
    private final DisplayLoad load = new DisplayLoad();
    private final TabRemovals tabRemovals = new TabRemovals();
    private volatile LongSupplier temporaryListingMillis = () -> DEFAULT_TEMPORARY_LISTING_MILLIS;
    private final int refreshTicks;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lobby-display");
        thread.setDaemon(true);
        return thread;
    });
    private @Nullable Task tickCounter;
    private volatile long ticks;

    public ClientObjectRenderer() {
        this(DEFAULT_REFRESH_TICKS);
    }

    /** @param refreshTicks how often viewers are re-checked (5 ticks = four times a second) */
    public ClientObjectRenderer(int refreshTicks) {
        this.refreshTicks = Math.max(1, refreshTicks);
    }

    /** Starts refreshing and forgets a player's entities when they leave. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerDisconnectEvent.class, event -> forget(event.getPlayer()));
        tickCounter = MinecraftServer.getSchedulerManager().buildTask(() -> ticks += refreshTicks)
                .repeat(TaskSchedule.tick(refreshTicks))
                .schedule();
        long millis = (long) refreshTicks * MinecraftServer.TICK_MS;
        worker.scheduleWithFixedDelay(this::refreshSafely, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Adds or replaces an object (same name = replace, with fresh packets for everyone who sees it). */
    public void put(ClientObject object) {
        worker.execute(() -> {
            Tracked previous = tracked.remove(object.name());
            if (previous != null) {
                despawnAll(previous);
            }
            tracked.put(object.name(), new Tracked(object));
        });
    }

    /** Removes an object and despawns it for everyone. */
    public void remove(String name) {
        worker.execute(() -> {
            Tracked previous = tracked.remove(name);
            if (previous != null) {
                despawnAll(previous);
            }
        });
    }

    /** Removes every object. */
    public void clear() {
        worker.execute(() -> {
            tracked.values().forEach(this::despawnAll);
            tracked.clear();
        });
    }

    /** Rebuilds one object's content on the next refresh (its text or items changed). */
    public void invalidate(String name) {
        worker.execute(() -> {
            Tracked entry = tracked.get(name);
            if (entry != null) {
                entry.contentDirty = true;
            }
        });
    }

    /** Rebuilds every object's content on the next refresh (after a reload). */
    public void invalidateAll() {
        worker.execute(() -> tracked.values().forEach(entry -> entry.contentDirty = true));
    }

    /** The object a clicked entity belongs to, or {@code null} if the entity is not ours. */
    public @Nullable String objectOf(int entityId) {
        return objectByEntityId.get(entityId);
    }

    /**
     * How long {@link EntityPart.Listing#TEMPORARY} entries stay in the tab list, in milliseconds. Read
     * on every spawn, so a reloaded setting applies to the next spawn.
     */
    public void temporaryListingDelay(LongSupplier millis) {
        temporaryListingMillis = millis;
    }

    /** How many temporary tab entries are waiting to be removed, for tests. */
    public int pendingTabRemovals() {
        try {
            return worker.submit(tabRemovals::pendingCount).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("could not count tab removals", e);
        }
    }

    /** How busy the display thread was over the last minute, for {@code /lobby info}. */
    public DisplayLoad.Snapshot load() {
        return load.snapshot();
    }

    /**
     * Runs other display work on this same thread and counts it in {@link #load()}, so one number
     * covers everything the display thread does (the scoreboard and tab list use this).
     */
    public void runOnDisplayThread(Runnable task) {
        worker.execute(() -> {
            long start = System.nanoTime();
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.error("Display task failed", e);
            } finally {
                load.record(start, System.nanoTime() - start);
            }
        });
    }

    /** How many refreshes ran, for tests and metrics. */
    public int refreshCount() {
        return refreshes.get();
    }

    /** How many times content was built, for tests and metrics (one per variant, not per viewer). */
    public int renderCount() {
        return renders.get();
    }

    /** Runs one refresh now and waits for it. For tests. */
    public void refreshNow() {
        try {
            worker.submit(this::refresh).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("display refresh failed", e);
        }
    }

    /** Stops refreshing. */
    public void shutdown() {
        if (tickCounter != null) {
            tickCounter.cancel();
        }
        worker.shutdownNow();
    }

    private void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException e) {
            LOGGER.error("Could not refresh the displayed objects", e);
        }
    }

    private void refresh() {
        long start = System.nanoTime();
        try {
            refreshObjects();
        } finally {
            load.record(start, System.nanoTime() - start);
        }
    }

    private void refreshObjects() {
        refreshes.incrementAndGet();
        tabRemovals.sendDue(System.nanoTime());
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        for (Tracked entry : tracked.values()) {
            // Both are asked, never short-circuited: a dirty object must still book its next update time,
            // or it would be built again on the very next refresh.
            boolean due = dueForUpdate(entry);
            boolean updateContent = entry.contentDirty || due;
            entry.contentDirty = false;
            try {
                update(entry, online, updateContent);
            } catch (RuntimeException e) {
                LOGGER.error("Could not show '{}'", entry.object.name(), e);
            }
        }
    }

    private boolean dueForUpdate(Tracked entry) {
        int interval = entry.object.updateIntervalTicks();
        if (interval <= 0 || ticks < entry.nextContentUpdate) {
            return false;
        }
        entry.nextContentUpdate = ticks + interval;
        return true;
    }

    /** Works out who should see what, then spawns, updates and despawns as little as possible. */
    private void update(Tracked entry, Collection<Player> online, boolean updateContent) {
        Map<Object, List<Player>> groups = groupViewers(entry.object, online);

        // Viewers who should no longer see this variant (out of range, lost visibility, left, or changed group).
        for (Map.Entry<Object, Variant> variant : entry.variants.entrySet()) {
            List<Player> wantedList = groups.get(variant.getKey());
            // A set, because a busy lobby can have hundreds of viewers in one group.
            Set<UUID> wanted = wantedList == null ? Set.of()
                    : wantedList.stream().map(Player::getUuid).collect(Collectors.toSet());
            List<Player> gone = new ArrayList<>();
            for (Player viewer : variant.getValue().viewers.values()) {
                if (!wanted.contains(viewer.getUuid())) {
                    gone.add(viewer);
                }
            }
            for (Player viewer : gone) {
                variant.getValue().viewers.remove(viewer.getUuid());
                entry.viewerVariants.remove(viewer.getUuid());
                entry.object.viewerGone(viewer);
                destroyFor(viewer, variant.getValue());
            }
        }

        for (Map.Entry<Object, List<Player>> group : groups.entrySet()) {
            Variant variant = entry.variants.get(group.getKey());
            boolean fresh = variant == null;
            if (fresh) {
                variant = new Variant();
                entry.variants.put(group.getKey(), variant);
            }
            List<EntityPart> before = variant.parts;
            if (fresh || updateContent) {
                variant.parts = render(entry.object, group.getKey(), group.getValue().getFirst());
                variant.ensureIds(variant.parts.size());
                variant.entityIds.forEach(id -> objectByEntityId.put(id, entry.object.name()));
            }
            List<Player> newViewers = new ArrayList<>();
            for (Player viewer : group.getValue()) {
                if (variant.viewers.putIfAbsent(viewer.getUuid(), viewer) == null) {
                    entry.viewerVariants.put(viewer.getUuid(), group.getKey());
                    newViewers.add(viewer);
                }
            }
            if (!newViewers.isEmpty()) {
                spawnTo(newViewers, variant);
            }
            if (!fresh && updateContent) {
                List<Player> existing = new ArrayList<>(variant.viewers.values());
                existing.removeAll(new HashSet<>(newViewers));
                sendUpdates(variant, before, existing);
            }
            if (entry.object.hasViewerPackets()) {
                sendViewerPackets(entry.object, variant, new HashSet<>(newViewers));
            }
        }

        entry.variants.entrySet().removeIf(variant -> {
            if (!variant.getValue().viewers.isEmpty()) {
                return false;
            }
            variant.getValue().entityIds.forEach(objectByEntityId::remove);
            return true;
        });
    }

    /** The players who may see the object, grouped by what they would see. */
    private Map<Object, List<Player>> groupViewers(ClientObject object, Collection<Player> online) {
        double maxDistanceSquared = object.viewDistance() * object.viewDistance();
        Map<Object, List<Player>> groups = new LinkedHashMap<>();
        WorldScope scope = object.scope();
        for (Player player : online) {
            // The world first: the same coordinates exist in every world, the distance alone proves nothing.
            if (!scope.includes(player.getInstance())
                    || player.getPosition().distanceSquared(object.position()) > maxDistanceSquared
                    || !object.visibleTo(player)) {
                continue;
            }
            groups.computeIfAbsent(object.variantKey(player), ignored -> new ArrayList<>()).add(player);
        }
        return groups;
    }

    private List<EntityPart> render(ClientObject object, Object key, Player viewer) {
        renders.incrementAndGet();
        try {
            return List.copyOf(object.render(key, viewer));
        } catch (RuntimeException e) {
            LOGGER.error("Could not build '{}' for {}", object.name(), viewer.getUsername(), e);
            return List.of();
        }
    }

    /** The per-viewer packets of an object, such as an NPC looking at each player near it. */
    private static void sendViewerPackets(ClientObject object, Variant variant, Set<Player> justSpawned) {
        for (Player viewer : variant.viewers.values()) {
            try {
                for (ServerPacket packet : object.viewerPackets(viewer, variant.entityIds, justSpawned.contains(viewer))) {
                    viewer.sendPacket(packet);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Could not update '{}' for {}", object.name(), viewer.getUsername(), e);
            }
        }
    }

    /** Sends only what changed; parts that disappeared are destroyed, new ones spawned. */
    private void sendUpdates(Variant variant, List<EntityPart> before, List<Player> viewers) {
        if (viewers.isEmpty() || before.equals(variant.parts)) {
            return;
        }
        List<ServerPacket> packets = new ArrayList<>();
        List<Integer> spawned = new ArrayList<>();
        boolean ridesChanged = false;
        for (int i = 0; i < variant.parts.size(); i++) {
            EntityPart now = variant.parts.get(i);
            if (i < before.size()) {
                EntityPart was = before.get(i);
                ridesChanged |= !was.passengers().equals(now.passengers());
                // A new skin needs a new player-list entry, which only a fresh spawn sends.
                if (was.type().equals(now.type()) && Objects.equals(was.uuid(), now.uuid()) && sameEntry(was, now)) {
                    List<ServerPacket> diff = PartPackets.update(variant.entityIds.get(i),
                            variant.uuidOf(i, variant.parts), was, now);
                    if (diff != null) {
                        packets.addAll(diff);
                    }
                    continue;
                }
                packets.addAll(destroyPackets(variant, i, i + 1, before, viewers));
            }
            packets.addAll(PartPackets.spawn(variant.entityIds.get(i), variant.uuidOf(i, variant.parts), now));
            spawned.add(i);
        }
        if (variant.parts.size() < before.size()) {
            packets.addAll(destroyPackets(variant, variant.parts.size(), before.size(), before, viewers));
        }
        if (ridesChanged || !spawned.isEmpty()) {
            // A respawned rider or seat has lost its ride on the client, so every ride is sent again.
            packets.addAll(PartPackets.passengers(variant.entityIds, variant.parts));
            for (int i = 0; i < Math.min(before.size(), variant.parts.size()); i++) {
                if (!before.get(i).passengers().isEmpty() && variant.parts.get(i).passengers().isEmpty()) {
                    packets.add(new SetPassengersPacket(variant.entityIds.get(i), List.of()));
                }
            }
        }
        send(viewers, packets);
        for (int i : spawned) {
            scheduleTabRemoval(viewers, variant, i);
        }
    }

    private static boolean sameEntry(EntityPart was, EntityPart now) {
        if (was.profile() == null || now.profile() == null) {
            return was.profile() == now.profile();
        }
        return was.profile().sameEntry(now.profile());
    }

    /** Spawns every part of a variant for new viewers, then their rides, then books temporary tab entries. */
    private void spawnTo(List<Player> viewers, Variant variant) {
        List<ServerPacket> packets = new ArrayList<>(variant.parts.size() * 2);
        for (int i = 0; i < variant.parts.size(); i++) {
            packets.addAll(PartPackets.spawn(variant.entityIds.get(i), variant.uuidOf(i, variant.parts),
                    variant.parts.get(i)));
        }
        packets.addAll(PartPackets.passengers(variant.entityIds, variant.parts));
        send(viewers, packets);
        for (int i = 0; i < variant.parts.size(); i++) {
            scheduleTabRemoval(viewers, variant, i);
        }
    }

    private void scheduleTabRemoval(List<Player> viewers, Variant variant, int index) {
        EntityPart part = variant.parts.get(index);
        if (part.profile() != null && part.profile().listing() == EntityPart.Listing.TEMPORARY) {
            long delay = TimeUnit.MILLISECONDS.toNanos(Math.max(0, temporaryListingMillis.getAsLong()));
            tabRemovals.schedule(viewers, variant.uuidOf(index, variant.parts), System.nanoTime() + delay);
        }
    }

    /**
     * Destroy packets for the parts {@code from} to {@code to} of {@code parts}, with their player-list
     * entries, and forgets those entries' pending removals for these viewers.
     */
    private List<ServerPacket> destroyPackets(Variant variant, int from, int to, List<EntityPart> parts,
                                              Collection<Player> viewers) {
        List<UUID> entries = new ArrayList<>();
        for (int i = from; i < to && i < parts.size(); i++) {
            if (parts.get(i).profile() != null) {
                UUID uuid = variant.uuidOf(i, parts);
                entries.add(uuid);
                for (Player viewer : viewers) {
                    tabRemovals.cancel(viewer, uuid);
                }
            }
        }
        List<ServerPacket> packets = new ArrayList<>(2);
        packets.add(PartPackets.destroy(variant.entityIds.subList(from, to)));
        if (!entries.isEmpty()) {
            packets.add(PartPackets.removeEntries(entries));
        }
        return packets;
    }

    /** Destroys a whole variant for one viewer. */
    private void destroyFor(Player viewer, Variant variant) {
        List<ServerPacket> packets = destroyPackets(variant, 0, variant.entityIds.size(), variant.parts,
                List.of(viewer));
        if (viewer.isOnline()) {
            packets.forEach(viewer::sendPacket);
        }
    }

    private static void send(List<Player> viewers, List<ServerPacket> packets) {
        for (ServerPacket packet : packets) {
            if (viewers.size() == 1) {
                viewers.getFirst().sendPacket(packet);
            } else {
                PacketSendingUtils.sendGroupedPacket(viewers, packet);
            }
        }
    }

    private void despawnAll(Tracked entry) {
        for (Variant variant : entry.variants.values()) {
            for (Player viewer : variant.viewers.values()) {
                entry.object.viewerGone(viewer);
                destroyFor(viewer, variant);
            }
            variant.entityIds.forEach(objectByEntityId::remove);
        }
        entry.variants.clear();
        entry.viewerVariants.clear();
    }

    private void forget(Player player) {
        worker.execute(() -> {
            tabRemovals.forget(player.getUuid());
            for (Tracked entry : tracked.values()) {
                Object key = entry.viewerVariants.remove(player.getUuid());
                if (key != null) {
                    Variant variant = entry.variants.get(key);
                    if (variant != null) {
                        variant.viewers.remove(player.getUuid());
                    }
                    entry.object.viewerGone(player);
                }
            }
        });
    }
}
