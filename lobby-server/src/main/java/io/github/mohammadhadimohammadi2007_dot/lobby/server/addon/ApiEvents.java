package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.AddonEvents;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

import java.util.function.Consumer;

/**
 * An addon's listeners, in one event node of its own under Minestom's global event handler, so they can be
 * removed together. Each listener is wrapped: an exception disables the addon instead of reaching the
 * lobby's code that fired the event.
 */
final class ApiEvents implements AddonEvents {

    private final AddonContextImpl context;
    private EventNode<Event> node;

    ApiEvents(AddonContextImpl context) {
        this.context = context;
    }

    @Override
    public <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        node().addListener(type, event -> {
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

    private synchronized EventNode<Event> node() {
        if (node == null) {
            node = EventNode.all("addon-" + context.description().id());
            MinecraftServer.getGlobalEventHandler().addChild(node);
        }
        return node;
    }

    /** Removes every listener of the addon. */
    synchronized void cleanup() {
        if (node != null) {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
            node = null;
        }
    }

    /** True if the addon listens to anything, for tests. */
    synchronized boolean listening() {
        return node != null;
    }
}
