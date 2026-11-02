package io.github.mohammadhadimohammadi2007_dot.lobby.api.event;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.minestom.server.event.Event;

import java.util.function.Consumer;

/**
 * Listens to events: the lobby's own (this package), every Minestom event, and events other addons fire, e.g.
 * {@code events().listen(NpcClickEvent.class, event -> ...)}.
 *
 * <p>A listener that throws disables the addon (with the error in the log); the event goes on to the other
 * listeners. Listeners are removed when the addon is disabled. Each event says on which thread it is
 * fired; never block there.
 *
 * <p>Addons can have events of their own: extend {@link LobbyPlayerEvent} (or {@link CancellableLobbyEvent}) or
 * implement Minestom's {@link Event}, and {@link #fire} them. Addons that depend on yours see the class and can
 * listen to it.
 */
@NonExtendable
public interface AddonEvents {

    /** Calls {@code listener} for every event of {@code type} (and its subtypes), at {@link EventPriority#NORMAL}. */
    default <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        listen(type, EventPriority.NORMAL, listener);
    }

    /** Calls {@code listener} for every event of {@code type} (and its subtypes), at the given priority. */
    <E extends Event> void listen(Class<E> type, EventPriority priority, Consumer<E> listener);

    /**
     * Calls every listener of the event (the lobby's, this addon's and other addons') on the calling thread, and
     * returns it, so the caller can check whether it was cancelled or changed.
     */
    <E extends Event> E fire(E event);
}
