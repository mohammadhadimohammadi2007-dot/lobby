package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The lobby's cosmetics: trails, hats, gadgets, pets, balloons and outfits from cosmetics.yml. Changes are followed
 * with {@code CosmeticChangeEvent} and {@code GadgetUseEvent}. Reading works on any thread; {@link #equip} and
 * {@link #unequip} on the tick thread.
 */
@NonExtendable
public interface Cosmetics {

    /** The kinds of cosmetics; a player uses at most one of each. */
    enum Kind {
        TRAIL, HAT, GADGET, PET, BALLOON, OUTFIT
    }

    /** False if cosmetics are turned off in cosmetics.yml. */
    boolean enabled();

    /** The ids of every cosmetic of a kind, in file order. */
    List<String> ids(Kind kind);

    /** The id the player uses of this kind, or {@code null}. A cosmetic they may no longer use counts as none. */
    @Nullable String equipped(Player player, Kind kind);

    /** True if the player has the cosmetic's permission (or it needs none). */
    boolean unlocked(Player player, Kind kind, String id);

    /**
     * Makes the player use a cosmetic and saves it, as a click in the menu does.
     *
     * @return false if there is no such cosmetic, the player has not unlocked it, their client cannot show it, or a
     *         listener cancelled the change
     */
    boolean equip(Player player, Kind kind, String id);

    /** Takes off the player's cosmetic of this kind, if any. */
    void unequip(Player player, Kind kind);

    /** True if the player chose not to see other players' trails, gadget effects, pets and balloons. */
    boolean hidesOthers(Player player);
}
