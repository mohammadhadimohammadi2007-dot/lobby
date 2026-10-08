package io.github.mohammadhadimohammadi2007_dot.lobby.server.region;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Knows which regions each player is in and tells the region's owner (portals, jump pads...) when a
 * player walks in or out. Moving inside the same block costs one comparison; regions are only looked up
 * when a player crosses into another block.
 */
public final class RegionTracker {

    private static final class State {
        long block = Long.MIN_VALUE;
        List<Region> inside = List.of();
    }

    private final Map<String, List<Region>> byOwner = new ConcurrentHashMap<>();
    private final Map<String, List<RegionListener>> listeners = new ConcurrentHashMap<>();
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile RegionIndex index = RegionIndex.EMPTY;

    /** Replaces every region of {@code owner} (e.g. after a portal was created or the config reloaded). */
    public synchronized void setRegions(String owner, List<Region> regions) {
        for (Region region : regions) {
            if (!region.owner().equals(owner)) {
                throw new IllegalArgumentException("region " + region.name() + " belongs to " + region.owner());
            }
        }
        if (regions.isEmpty()) {
            byOwner.remove(owner);
        } else {
            byOwner.put(owner, List.copyOf(regions));
        }
        List<Region> all = new ArrayList<>();
        byOwner.values().forEach(all::addAll);
        index = new RegionIndex(all);
    }

    /** Calls {@code listener} for players entering and leaving regions of {@code owner}. */
    public void listen(String owner, RegionListener listener) {
        listeners.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** Regions containing the block, for features that ask instead of listening. */
    public List<Region> regionsAt(Point point) {
        return index.at(point.blockX(), point.blockY(), point.blockZ());
    }

    /** Starts following player movement. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerMoveEvent.class, event -> {
            if (!event.isCancelled()) {
                update(event.getPlayer(), event.getNewPosition());
            }
        });
        node.addListener(PlayerSpawnEvent.class, event -> {
            // A new instance (or first join): start fresh so regions at the spawn point count as entered.
            states.remove(event.getPlayer().getUuid());
            update(event.getPlayer(), event.getPlayer().getPosition());
        });
        node.addListener(PlayerDisconnectEvent.class, event -> states.remove(event.getPlayer().getUuid()));
    }

    /** Checks the player's position and fires enter/leave. Package-private for tests. */
    void update(Player player, Point position) {
        State state = states.computeIfAbsent(player.getUuid(), ignored -> new State());
        long block = pack(position.blockX(), position.blockY(), position.blockZ());
        if (block == state.block) {
            return;
        }
        state.block = block;
        List<Region> now = index.at(position.blockX(), position.blockY(), position.blockZ());
        List<Region> before = state.inside;
        if (now.isEmpty() && before.isEmpty()) {
            return;
        }
        state.inside = now;
        for (Region region : before) {
            if (!now.contains(region)) {
                notify(player, region, false);
            }
        }
        for (Region region : now) {
            if (!before.contains(region)) {
                notify(player, region, true);
            }
        }
    }

    /** Regions the player is in right now. */
    public List<Region> regionsOf(Player player) {
        State state = states.get(player.getUuid());
        return state == null ? List.of() : state.inside;
    }

    private void notify(Player player, Region region, boolean entered) {
        List<RegionListener> owners = listeners.get(region.owner());
        if (owners == null) {
            return;
        }
        for (RegionListener listener : owners) {
            if (entered) {
                listener.entered(player, region);
            } else {
                listener.left(player, region);
            }
        }
    }

    /** Block coordinates in one long, as Minecraft packs block positions. */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
