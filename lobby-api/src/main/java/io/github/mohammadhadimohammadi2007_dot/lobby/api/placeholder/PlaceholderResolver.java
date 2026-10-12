package io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder;

import org.jetbrains.annotations.Nullable;

/**
 * All placeholders of one namespace. For {@code %myaddon_top_1_name%} in the namespace {@code myaddon} the
 * params are {@code top_1_name}.
 *
 * <p>Called once per distinct placeholder text; the answer is remembered. So it may parse the params, and
 * the returned {@link PlaceholderValue} is what runs every time the text is shown.
 */
@FunctionalInterface
public interface PlaceholderResolver {

    /** How to compute the placeholder, or {@code null} if this namespace does not know it (it is shown as written). */
    @Nullable PlaceholderValue resolve(String params);
}
