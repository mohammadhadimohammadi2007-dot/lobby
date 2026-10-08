package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.TextNormalizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of words from a filter file, ready for fast matching.
 *
 * <p>File format: one entry per line; lines starting with {@code #} and empty lines are ignored.
 * <ul>
 *   <li>{@code word}: matches the whole word only ("ass" does not match "class")</li>
 *   <li>{@code word*}: matches words that start with it ("fuck*" matches "fucking")</li>
 *   <li>{@code *word}: matches words that end with it</li>
 *   <li>{@code *word*}: matches anywhere, even inside other words</li>
 * </ul>
 * Entries are normalized like chat (see {@link TextNormalizer}), so one entry also catches
 * {@code F.U.C.K}-style, leetspeak, look-alike and Arabic/Persian spelling variants.
 */
final class WordList {

    /**
     * One entry.
     *
     * @param original      the line as written in the file, for logs
     * @param pattern       normalized form
     * @param repeats       repeat count per pattern character (see {@link TextNormalizer.Normalized#repeats()})
     * @param anyStart      true for {@code *word}: may start inside a word
     * @param anyEnd        true for {@code word*}: may end inside a word
     */
    record Entry(String original, String pattern, int[] repeats, boolean anyStart, boolean anyEnd) {
    }

    private static final char WILDCARD = '*';

    private final List<Entry> entries;
    private final AhoCorasick automaton;

    private WordList(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.automaton = new AhoCorasick(entries.stream().map(Entry::pattern).toList());
    }

    /** Builds a list from file lines. */
    static WordList parse(List<String> lines) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            boolean anyStart = trimmed.charAt(0) == WILDCARD;
            boolean anyEnd = trimmed.length() > 1 && trimmed.charAt(trimmed.length() - 1) == WILDCARD;
            String word = trimmed.substring(anyStart ? 1 : 0, trimmed.length() - (anyEnd ? 1 : 0));
            TextNormalizer.Normalized normalized = TextNormalizer.forFilter(word);
            if (normalized.text().isBlank() || !seen.add(trimmed)) {
                continue;
            }
            int[] repeats = normalized.repeats().clone();
            for (int i = 0; i < repeats.length; i++) {
                if (normalized.text().charAt(i) == ' ') {
                    repeats[i] = 1; // "kill  yourself" in a list must not require two spaces in chat
                }
            }
            entries.add(new Entry(trimmed, normalized.text(), repeats, anyStart, anyEnd));
        }
        return new WordList(entries);
    }

    static WordList empty() {
        return new WordList(List.of());
    }

    int size() {
        return entries.size();
    }

    /**
     * Every entry found in {@code text}, as a range of normalized character indexes (inclusive).
     *
     * @param text normalized chat text
     */
    List<Found> find(TextNormalizer.Normalized text) {
        if (entries.isEmpty()) {
            return List.of();
        }
        String chars = text.text();
        List<Found> found = new ArrayList<>();
        for (AhoCorasick.Match match : automaton.search(chars)) {
            Entry entry = entries.get(match.pattern());
            int end = match.end();
            int start = end - automaton.length(match.pattern()) + 1;
            if (!entry.anyStart() && start > 0 && TextNormalizer.isWordChar(chars.charAt(start - 1))) {
                continue;
            }
            if (!entry.anyEnd() && end + 1 < chars.length() && TextNormalizer.isWordChar(chars.charAt(end + 1))) {
                continue;
            }
            if (!repeatsAllow(text.repeats(), start, entry.repeats())) {
                continue;
            }
            found.add(new Found(entry, start, end));
        }
        return found;
    }

    /** Each letter must repeat at least as often as in the entry ("as" does not match the entry "ass"). */
    private static boolean repeatsAllow(int[] textRepeats, int start, int[] entryRepeats) {
        for (int k = 0; k < entryRepeats.length; k++) {
            if (textRepeats[start + k] < entryRepeats[k]) {
                return false;
            }
        }
        return true;
    }

    /** An entry found in a text, as normalized indexes {@code start..end} (inclusive). */
    record Found(Entry entry, int start, int end) {
    }
}
