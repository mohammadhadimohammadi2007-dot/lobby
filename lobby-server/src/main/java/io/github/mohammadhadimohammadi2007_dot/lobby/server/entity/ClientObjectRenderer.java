package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.PacketSendingUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shows {@link ClientObject}s (holograms, NPCs) to the players near them.
 *
 * <p>Everything here is packets, so one object automatically exists in every lobby instance and costs no
 * tick time. Viewers who would see exactly the same thing share one set of entity ids, so a hologram
 * without player placeholders is built once however many players read it. Viewers who walk out of range
 * get a destroy packet and a fresh spawn when they come back.
 *
 * <p>All of it runs on one background thread, so building text never slows the server tick.
 */
public final class ClientObjectRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientObjectRenderer.class);
    private static final int DEFAULT_REFRESH_TICKS = 5;

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
        refreshes.incrementAndGet();
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        for (Tracked entry : tracked.values()) {
            boolean updateContent = entry.contentDirty || dueForUpdate(entry);
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
            List<Player> wanted = groups.get(variant.getKey());
            List<Player> gone = new ArrayList<>();
            for (Player viewer : variant.getValue().viewers.values()) {
                if (wanted == null || !wanted.contains(viewer)) {
                    gone.add(viewer);
                }
            }
            for (Player viewer : gone) {
                variant.getValue().viewers.remove(viewer.getUuid());
                entry.viewerVariants.remove(viewer.getUuid());
                if (viewer.isOnline()) {
                    viewer.sendPacket(PartPackets.destroy(variant.getValue().entityIds));
                }
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
                send(newViewers, spawnPackets(variant));
            }
            if (!fresh && updateContent) {
                List<Player> existing = new ArrayList<>(variant.viewers.values());
                existing.removeAll(newViewers);
                sendUpdates(variant, before, existing);
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
        for (Player player : online) {
            if (player.getPosition().distanceSquared(object.position()) > maxDistanceSquared
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

    /** Sends only what changed; parts that disappeared are destroyed, new ones spawned. */
    private void sendUpdates(Variant variant, List<EntityPart> before, List<Player> viewers) {
        if (viewers.isEmpty() || before.equals(variant.parts)) {
            return;
        }
        List<ServerPacket> packets = new ArrayList<>();
        for (int i = 0; i < variant.parts.size(); i++) {
            EntityPart now = variant.parts.get(i);
            if (i < before.size()) {
                EntityPart was = before.get(i);
                if (was.type().equals(now.type())) {
                    List<ServerPacket> diff = PartPackets.update(variant.entityIds.get(i), was, now);
                    if (diff != null) {
                        packets.addAll(diff);
                    }
                    continue;
                }
                packets.add(PartPackets.destroy(List.of(variant.entityIds.get(i))));
            }
            packets.addAll(PartPackets.spawn(variant.entityIds.get(i), variant.uuids.get(i), now));
        }
        if (variant.parts.size() < before.size()) {
            packets.add(PartPackets.destroy(variant.entityIds.subList(variant.parts.size(), before.size())));
        }
        send(viewers, packets);
    }

    private List<ServerPacket> spawnPackets(Variant variant) {
        List<ServerPacket> packets = new ArrayList<>(variant.parts.size() * 2);
        for (int i = 0; i < variant.parts.size(); i++) {
            packets.addAll(PartPackets.spawn(variant.entityIds.get(i), variant.uuids.get(i), variant.parts.get(i)));
        }
        return packets;
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
                if (viewer.isOnline()) {
                    viewer.sendPacket(PartPackets.destroy(variant.entityIds));
                }
            }
            variant.entityIds.forEach(objectByEntityId::remove);
        }
        entry.variants.clear();
        entry.viewerVariants.clear();
    }

    private void forget(Player player) {
        worker.execute(() -> {
            for (Tracked entry : tracked.values()) {
                Object key = entry.viewerVariants.remove(player.getUuid());
                if (key != null) {
                    Variant variant = entry.variants.get(key);
                    if (variant != null) {
                        variant.viewers.remove(player.getUuid());
                    }
                }
            }
        });
    }
}
