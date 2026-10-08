package io.github.mohammadhadimohammadi2007_dot.lobby.server.entity;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.List;

/**
 * Something shown to players with packets only: a hologram, an NPC, a floating item. The renderer asks
 * which viewers may see it and what it looks like for each group of viewers.
 */
public interface ClientObject {

    /** Unique name among objects of the same kind, for logs and commands. */
    String name();

    /** Where it is. Viewers further away than {@link #viewDistance()} do not get it. */
    Pos position();

    /**
     * Which worlds it belongs to. Checked before the distance, because the same coordinates exist in
     * every world: see {@link WorldScope}.
     */
    WorldScope scope();

    /** How far away it is still shown, in blocks. */
    double viewDistance();

    /** True if this viewer may see it at all (permission, visibility rules, player settings). */
    default boolean visibleTo(Player viewer) {
        return true;
    }

    /**
     * What decides how this object looks for {@code viewer}. Viewers with equal keys see exactly the same
     * thing, so their packets are built once and shared. Keep it cheap: it runs for every nearby viewer on
     * every refresh. Return a constant for objects that look the same for everybody.
     */
    Object variantKey(Player viewer);

    /**
     * How often the content is rebuilt, in ticks. 0 (the default) means it only changes when something
     * says so, which is right for text without placeholders; a hologram showing the player count sets an
     * interval. Viewers are still re-checked every refresh, however this is set.
     */
    default int updateIntervalTicks() {
        return 0;
    }

    /**
     * The entities to send for one variant. Called once per variant per refresh, never once per viewer.
     *
     * @param key    the key {@link #variantKey} returned for this group
     * @param viewer one viewer of the group, for placeholders
     */
    List<EntityPart> render(Object key, Player viewer);
}
