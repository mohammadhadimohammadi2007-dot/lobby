package io.github.mohammadhadimohammadi2007_dot.lobby.api.hologram;

import net.minestom.server.coordinate.Pos;

import java.util.List;
import java.util.Set;

/**
 * The lobby's holograms. They are saved in data/holograms.yml like the ones made with {@code /hologram}, so
 * an addon should create its holograms once (check {@link #exists} first) and admins can edit them too.
 * Thread-safe.
 *
 * <p>Names: lowercase letters, digits, {@code -} and {@code _}. Methods that change a hologram return false
 * if there is none with that name.
 */
public interface Holograms {

    /** Every hologram's name. */
    Set<String> names();

    boolean exists(String name);

    /** Creates a text hologram. False if the name is taken or invalid. */
    boolean create(String name, Pos position, List<String> lines);

    /** Replaces the lines (MiniMessage, placeholders allowed). */
    boolean setLines(String name, List<String> lines);

    boolean move(String name, Pos position);

    /**
     * Replaces the actions run when a player clicks it.
     *
     * @return what is wrong with entries that cannot be read (they are skipped); empty if all are fine
     * @throws IllegalArgumentException if there is no such hologram
     */
    List<String> setActions(String name, List<String> actions);

    boolean delete(String name);
}
