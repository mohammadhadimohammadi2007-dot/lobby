package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Clicks on holograms and NPCs.
 *
 * <p>These only exist in packets, so Minestom has no entity for them and fires no entity event. The
 * raw packets are read instead: {@code ClientAttackPacket} for a left click and
 * {@code ClientInteractEntityPacket} for a right click. The entity id is turned back into the object
 * it belongs to through the renderer, which knows the ids it gave to each group of viewers.
 *
 * <p>Only the main hand counts, because a client sends the interact packet for both hands and the
 * click would otherwise run twice. Clicks are also rate-limited per player, so holding the mouse
 * button down cannot flood the action system; each action list has its own cooldown on top.
 */
public final class ClientObjectClicks {

    /** Which mouse button was used. */
    public enum ClickType {
        /** Left click: attack. */
        LEFT,
        /** Right click: use. */
        RIGHT
    }

    /** Something that owns client objects and wants their clicks. */
    public interface Handler {
        /** Handles a click, or returns false if this object is not one of ours. */
        boolean clicked(Player player, String objectName, ClickType type);
    }

    /** Shortest time between two clicks of the same player that are passed on, in milliseconds. */
    private static final long MIN_CLICK_INTERVAL_MILLIS = 150;

    private final ClientObjectRenderer renderer;
    private final List<Handler> handlers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public ClientObjectClicks(ClientObjectRenderer renderer) {
        this.renderer = renderer;
    }

    /** Adds a handler. Handlers are asked in order until one says the object is theirs. */
    public void addHandler(Handler handler) {
        handlers.add(handler);
    }

    /** Starts listening for clicks. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerPacketEvent.class, event -> {
            ClickType type = clickType(event);
            if (type != null) {
                handle(event.getPlayer(), targetId(event.getPacket()), type);
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> lastClick.remove(event.getPlayer().getUuid()));
    }

    /** Runs a click as if it came from the client. For tests. */
    public boolean click(Player player, int entityId, ClickType type) {
        return handle(player, entityId, type);
    }

    private boolean handle(Player player, int entityId, ClickType type) {
        String objectName = renderer.objectOf(entityId);
        if (objectName == null || !passRateLimit(player.getUuid())) {
            return false;
        }
        for (Handler handler : handlers) {
            if (handler.clicked(player, objectName, type)) {
                return true;
            }
        }
        return false;
    }

    private boolean passRateLimit(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.get(player);
        if (previous != null && now - previous < MIN_CLICK_INTERVAL_MILLIS) {
            return false;
        }
        lastClick.put(player, now);
        return true;
    }

    /** The kind of click, or {@code null} if the packet is not a click we care about. */
    private static @Nullable ClickType clickType(PlayerPacketEvent event) {
        if (event.getPacket() instanceof ClientAttackPacket) {
            return ClickType.LEFT;
        }
        if (event.getPacket() instanceof ClientInteractEntityPacket interact) {
            // Both hands send this packet; taking only the main hand keeps one click one click.
            return interact.hand() == PlayerHand.MAIN ? ClickType.RIGHT : null;
        }
        return null;
    }

    private static int targetId(Object packet) {
        if (packet instanceof ClientAttackPacket attack) {
            return attack.targetId();
        }
        return ((ClientInteractEntityPacket) packet).targetId();
    }

    /** The handlers, for tests. */
    List<Handler> handlers() {
        return new ArrayList<>(handlers);
    }
}
