package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import net.minestom.server.event.Event;

import java.util.function.Consumer;

/**
 * Listens to events: the lobby's own (this package) and every Minestom event, e.g.
 * {@code events().listen(NpcClickEvent.class, event -> ...)}.
 *
 * <p>A listener that throws disables the addon (with the error in the log); the event goes on to the other
 * listeners. Listeners are removed when the addon is disabled. Each event says on which thread it is
 * fired; never block there.
 */
public interface AddonEvents {

    /** Calls {@code listener} for every event of {@code type} (and its subtypes). */
    <E extends Event> void listen(Class<E> type, Consumer<E> listener);
}
