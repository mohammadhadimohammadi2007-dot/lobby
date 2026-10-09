package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.FormattedText;
import org.jetbrains.annotations.Nullable;

/**
 * A text from {@code display.yml} with an optional version for 1.8-1.12 clients. Both are MiniMessage
 * templates; {@code &} colour codes are turned into MiniMessage when read, so either works.
 *
 * @param modern the text for most clients
 * @param legacy the text for 1.8-1.12, or {@code null} to shorten {@link #modern} for them
 */
public record TieredText(String modern, @Nullable String legacy) {

    public static final TieredText EMPTY = new TieredText("", null);

    /** A text as written in the file, with {@code &} codes allowed. */
    public static TieredText of(String modern, @Nullable String legacy) {
        return new TieredText(FormattedText.legacyToMiniMessage(modern),
                legacy == null ? null : FormattedText.legacyToMiniMessage(legacy));
    }

    /** The template for one kind of client. */
    public String template(boolean legacyClient) {
        return legacyClient && legacy != null ? legacy : modern;
    }

    public boolean isEmpty() {
        return modern.isEmpty() && (legacy == null || legacy.isEmpty());
    }
}
