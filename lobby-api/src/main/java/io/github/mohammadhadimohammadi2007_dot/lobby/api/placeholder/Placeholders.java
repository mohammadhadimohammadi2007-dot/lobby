package io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder;

import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders like {@code %myaddon_level%}, usable in every text of the lobby (holograms, NPC names,
 * scoreboard, tab list, chat formats, menus, messages). Everything after the first {@code _} is passed to
 * the namespace's {@link PlaceholderResolver}.
 *
 * <p>Register namespaces in {@code onLoad}, before the lobby reads its files: a hologram decides how often
 * it has to be rebuilt from the placeholders in it when it is read. Registering later throws
 * {@link IllegalStateException}. Namespaces are removed when the addon is disabled.
 */
public interface Placeholders {

    /**
     * Adds a namespace.
     *
     * @param namespace lowercase letters and digits only, e.g. {@code myaddon} for {@code %myaddon_...%}
     * @throws IllegalArgumentException if the name is invalid or already taken
     */
    void register(String namespace, PlaceholderResolver resolver);

    /** {@code text} with every placeholder filled in for {@code player} ({@code null}: global ones only), as plain text. */
    String fill(String text, @Nullable Player player);
}
