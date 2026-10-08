package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Which version of a chat message a viewer gets. Messages are rendered once per key, not once per viewer:
 * with 200 viewers there are usually only a handful of keys.
 *
 * @param legacy    the viewer's client is older than 1.19.4
 * @param persian   the viewer wants Persian text fixed
 * @param mentioned the viewer's id if this viewer is mentioned (their name is highlighted), otherwise {@code null}
 * @param staff     the viewer is staff and sees the message id
 */
public record VariantKey(boolean legacy, boolean persian, @Nullable UUID mentioned, boolean staff) {
}
