package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import org.jetbrains.annotations.Nullable;

/**
 * Which worlds a {@link ClientObject} belongs to.
 *
 * <p>Client objects are sent as packets, not placed in a world, so without this a hologram at 0/65/0
 * would also appear at 0/65/0 in every other world the server runs (a parkour map, a PvP arena). The
 * renderer asks this before it measures the distance.
 *
 * <p>{@link #mainMap} is what holograms and NPCs use: every lobby instance of the loaded map, because
 * those instances are copies of the same build and all of them should show it.
 */
public interface WorldScope {

    /** True if a viewer in {@code instance} may see the object. */
    boolean includes(@Nullable Instance instance);

    /** The loaded lobby map and every lobby instance sharing it. */
    static WorldScope mainMap(InstanceContainer map) {
        return instance -> instance == map
                || (instance instanceof SharedInstance shared && shared.getInstanceContainer() == map);
    }

    /** One instance only, for an object that belongs to a single lobby. */
    static WorldScope only(Instance instance) {
        return other -> other == instance;
    }

    /** Every world on the server. Only right for something that is part of no build at all. */
    static WorldScope anywhere() {
        return instance -> instance != null;
    }
}
