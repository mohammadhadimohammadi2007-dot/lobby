package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.render;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;

import java.util.Locale;
import java.util.Map;

/** Replaces {@code :name:} shortcodes with emoji symbols, or their text fallback for old clients. */
public final class EmojiReplacer {

    /** Longest shortcode name looked at, so a stray colon never scans the whole message. */
    private static final int MAX_NAME = 32;

    private EmojiReplacer() {
    }

    /**
     * @param emojis the emoji list from chat.yml
     * @param legacy true for clients older than 1.19.4, which get the text fallback
     */
    public static String apply(String text, Map<String, ChatConfig.Emoji> emojis, boolean legacy) {
        if (emojis.isEmpty() || text.indexOf(':') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ':') {
                int end = text.indexOf(':', i + 1);
                if (end > i + 1 && end - i - 1 <= MAX_NAME) {
                    ChatConfig.Emoji emoji = emojis.get(text.substring(i + 1, end).toLowerCase(Locale.ROOT));
                    if (emoji != null) {
                        out.append(legacy ? emoji.legacy() : emoji.symbol());
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
