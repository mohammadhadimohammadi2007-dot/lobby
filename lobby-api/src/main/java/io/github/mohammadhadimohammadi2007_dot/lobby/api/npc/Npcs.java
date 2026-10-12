package io.github.mohammadhadimohammadi2007_dot.lobby.api.npc;

import net.minestom.server.coordinate.Pos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The lobby's NPCs. They are saved in data/npcs.yml like the ones made with {@code /npc}, so an addon should
 * create its NPCs once (check {@link #exists} first) and admins can edit them too. Thread-safe.
 *
 * <p>Names: lowercase letters, digits, {@code -} and {@code _}. Methods that change an NPC return false if
 * there is none with that name.
 */
public interface Npcs {

    /** Which clicks run an action list. */
    enum Trigger {
        ANY_CLICK,
        LEFT_CLICK,
        RIGHT_CLICK
    }

    /** Every NPC's name. */
    Set<String> names();

    boolean exists(String name);

    /** Creates a player NPC with the default skin. False if the name is taken or invalid. */
    boolean create(String name, Pos position);

    /** Replaces the lines above its head (MiniMessage, placeholders allowed). */
    boolean setNameLines(String name, List<String> lines);

    boolean move(String name, Pos position);

    /**
     * Sets the skin as {@code /npc skin} does: a player name, {@code @mirror}, or a texture URL.
     *
     * @return completes with true once the skin is found and shown
     */
    CompletableFuture<Boolean> setSkin(String name, String skin);

    /**
     * Replaces the actions run on a click.
     *
     * @return what is wrong with entries that cannot be read (they are skipped); empty if all are fine
     * @throws IllegalArgumentException if there is no such NPC
     */
    List<String> setActions(String name, Trigger trigger, List<String> actions);

    boolean delete(String name);
}
