package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-list entries that were listed on purpose for a short while and must leave the viewer's tab
 * list again (see {@link EntityPart.Listing#TEMPORARY}).
 *
 * <p>Only used on the display thread. Everything due for one viewer goes in one packet, so ten NPCs
 * spawning together cost one packet per viewer, not ten.
 */
final class TabRemovals {

    /** Viewer, then entry, then when it is due (from {@link System#nanoTime()}). */
    private final Map<UUID, Map<UUID, Long>> due = new HashMap<>();
    private final Map<UUID, Player> viewers = new HashMap<>();

    /** Removes {@code entry} from these viewers' tab lists at {@code dueNanos}; a later call for the same pair wins. */
    void schedule(Collection<Player> forViewers, UUID entry, long dueNanos) {
        for (Player viewer : forViewers) {
            viewers.put(viewer.getUuid(), viewer);
            due.computeIfAbsent(viewer.getUuid(), ignored -> new LinkedHashMap<>()).put(entry, dueNanos);
        }
    }

    /** Forgets a pending removal, because the entry was removed together with its entity. */
    void cancel(Player viewer, UUID entry) {
        Map<UUID, Long> entries = due.get(viewer.getUuid());
        if (entries != null && entries.remove(entry) != null && entries.isEmpty()) {
            due.remove(viewer.getUuid());
            viewers.remove(viewer.getUuid());
        }
    }

    /** The viewer left, so their client has no tab list to clean. */
    void forget(UUID viewer) {
        due.remove(viewer);
        viewers.remove(viewer);
    }

    /** Sends every removal that is due: one packet per viewer, holding all of their due entries. */
    void sendDue(long now) {
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> byViewer = due.entrySet().iterator();
        while (byViewer.hasNext()) {
            Map.Entry<UUID, Map<UUID, Long>> pending = byViewer.next();
            List<UUID> ready = new ArrayList<>();
            pending.getValue().entrySet().removeIf(entry -> {
                if (entry.getValue() - now > 0) {
                    return false;
                }
                ready.add(entry.getKey());
                return true;
            });
            Player viewer = viewers.get(pending.getKey());
            if (!ready.isEmpty() && viewer != null && viewer.isOnline()) {
                viewer.sendPacket(PartPackets.removeEntries(ready));
            }
            if (pending.getValue().isEmpty()) {
                byViewer.remove();
                viewers.remove(pending.getKey());
            }
        }
    }

    /** How many removals are waiting, for tests. */
    int pendingCount() {
        return due.values().stream().mapToInt(Map::size).sum();
    }
}
