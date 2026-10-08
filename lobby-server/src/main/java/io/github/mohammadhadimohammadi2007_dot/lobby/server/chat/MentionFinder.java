package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Finds player names in a message ({@code Steve} or {@code @Steve}) and {@code @everyone}. */
public final class MentionFinder {

    /** Names shorter than this only count with {@code @}, so "Al" or "Me" do not ping by accident. */
    private static final int MIN_PLAIN_NAME = 3;
    private static final String EVERYONE = "everyone";

    private MentionFinder() {
    }

    /**
     * Mentions found in {@code text}.
     *
     * @param players online players: lower-case name to (id, name)
     * @param sender  the sender, never mentioned
     */
    public static Result find(String text, Map<String, Map.Entry<UUID, String>> players, UUID sender) {
        Map<UUID, String> found = new LinkedHashMap<>();
        boolean everyone = false;
        int i = 0;
        while (i < text.length()) {
            if (!isNameChar(text.charAt(i)) && text.charAt(i) != '@') {
                i++;
                continue;
            }
            int start = i;
            boolean at = text.charAt(i) == '@';
            if (at) {
                i++;
            }
            int nameStart = i;
            while (i < text.length() && isNameChar(text.charAt(i))) {
                i++;
            }
            String word = text.substring(nameStart, i).toLowerCase(Locale.ROOT);
            if (word.isEmpty()) {
                i = Math.max(i, start + 1);
                continue;
            }
            if (at && word.equals(EVERYONE)) {
                everyone = true;
                continue;
            }
            Map.Entry<UUID, String> player = players.get(word);
            if (player != null && !player.getKey().equals(sender) && (at || word.length() >= MIN_PLAIN_NAME)) {
                found.put(player.getKey(), player.getValue());
            }
        }
        return new Result(found, everyone);
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    /**
     * @param mentioned id to name of every mentioned player
     * @param everyone  true if {@code @everyone} was written (the permission is checked separately)
     */
    public record Result(Map<UUID, String> mentioned, boolean everyone) {
    }
}
