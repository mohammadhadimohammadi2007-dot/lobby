package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A chat message ready to be shown. Each {@link VariantKey} is rendered at most once and then reused for
 * every viewer that needs the same version. Only used on the chat thread.
 */
public final class RenderedMessage {

    private final Function<VariantKey, Component> renderer;
    private final Map<VariantKey, Component> variants = new HashMap<>(4);
    private int recipients;

    public RenderedMessage(Function<VariantKey, Component> renderer) {
        this.renderer = renderer;
    }

    /** The message as a viewer with {@code key} sees it. */
    public Component variant(VariantKey key) {
        return variants.computeIfAbsent(key, renderer);
    }

    /** How many different versions were rendered (for performance checks). */
    public int renderCount() {
        return variants.size();
    }

    /** Counts one more player who received the message. */
    public void addRecipient() {
        recipients++;
    }

    /** Players who received the message on this lobby. */
    public int recipients() {
        return recipients;
    }
}
