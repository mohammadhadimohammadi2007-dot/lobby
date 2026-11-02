package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.AddonEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.EventPriority;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An addon's listeners, in event nodes of its own under Minestom's global event handler (one per priority, named
 * {@code addon-<id>} for {@link EventPriority#NORMAL} and {@code addon-<id>-<priority>} for the others), so they can
 * be removed together. Minestom calls nodes with a lower priority number first, which orders the priorities across
 * every addon and the lobby's own listeners (which stay at {@link #NORMAL_ORDER}). Each listener is wrapped: an
 * exception disables the addon instead of reaching the lobby's code that fired the event.
 */
final class ApiEvents implements AddonEvents {

    /** The node priority of {@link EventPriority#NORMAL}, the same as the lobby's own listeners. */
    static final int NORMAL_ORDER = 0;

    private final AddonContextImpl context;
    private final Map<EventPriority, EventNode<Event>> nodes = new EnumMap<>(EventPriority.class);

    ApiEvents(AddonContextImpl context) {
        this.context = context;
    }

    @Override
    public <E extends Event> void listen(Class<E> type, EventPriority priority, Consumer<E> listener) {
        node(priority).addListener(type, event -> {
            if (!context.running()) {
                return;
            }
            try {
                listener.accept(event);
            } catch (RuntimeException | LinkageError e) {
                context.fail("a listener for " + type.getSimpleName(), e);
            }
        });
        LobbyEvents.activate();
    }

    @Override
    public <E extends Event> E fire(E event) {
        MinecraftServer.getGlobalEventHandler().call(event);
        return event;
    }

    /** Minestom's node priority for an addon priority: lower runs first. */
    static int order(EventPriority priority) {
        return switch (priority) {
            case EARLY -> -100;
            case NORMAL -> NORMAL_ORDER;
            case LATE -> 100;
            case MONITOR -> 10_000;
        };
    }

    private synchronized EventNode<Event> node(EventPriority priority) {
        return nodes.computeIfAbsent(priority, key -> {
            String name = "addon-" + context.description().id()
                    + (key == EventPriority.NORMAL ? "" : "-" + key.name().toLowerCase(java.util.Locale.ROOT));
            EventNode<Event> node = EventNode.all(name);
            node.setPriority(order(key));
            MinecraftServer.getGlobalEventHandler().addChild(node);
            return node;
        });
    }

    /** Removes every listener of the addon. */
    synchronized void cleanup() {
        for (EventNode<Event> node : new ArrayList<>(nodes.values())) {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
        nodes.clear();
    }

    /** True if the addon listens to anything, for tests. */
    synchronized boolean listening() {
        return !nodes.isEmpty();
    }

    /** The names of the addon's nodes, for tests. */
    synchronized List<String> nodeNames() {
        return nodes.values().stream().map(EventNode::getName).toList();
    }
}
