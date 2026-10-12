package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;

/**
 * Fires the API's events (NPC clicks, chat, regions...) to addons.
 *
 * <p>Until an addon listens to anything, {@link #active()} is false and the lobby does not even create the
 * event objects, so a server without addons pays nothing for them.
 */
public final class LobbyEvents {

    private static volatile boolean active;

    private LobbyEvents() {
    }

    /** True once an addon listens to events. Callers check this before building an event. */
    public static boolean active() {
        return active;
    }

    static void activate() {
        active = true;
    }

    /** Back to "nobody listens", for tests. */
    static void deactivate() {
        active = false;
    }

    /** Calls every listener of the event and returns it, so a caller can check whether it was cancelled. */
    public static <E extends Event> E fire(E event) {
        if (active) {
            MinecraftServer.getGlobalEventHandler().call(event);
        }
        return event;
    }
}
