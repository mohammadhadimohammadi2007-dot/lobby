package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import org.jetbrains.annotations.Nullable;

/**
 * All placeholders that start with one prefix, like PlaceholderAPI's expansions. For
 * {@code %luckperms_meta_rank%} the namespace is {@code luckperms} and the params are {@code meta_rank}.
 *
 * <p>{@link #lookup} is called once per distinct placeholder text and the result is remembered, so it
 * may do some parsing; the returned {@link Placeholder} is what runs every time.
 */
@FunctionalInterface
public interface PlaceholderNamespace {

    /**
     * @param params everything after {@code namespace_}, never empty
     * @return how to compute the placeholder, or {@code null} if this namespace does not know it
     */
    @Nullable Placeholder lookup(String params);
}
