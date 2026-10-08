package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.TextNormalizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds blocked and censored words in chat text using the lists from the {@code filters/} folder.
 *
 * <p>Two passes: the normal one on the normalized text, and an "evasion" pass that joins letters separated
 * by spaces or punctuation ({@code f u c k}, {@code f.u.c.k}). Only runs of single letters are joined, so
 * normal words next to each other never merge into a false match. Words in the allowed list are never hit.
 */
final class WordFilter {

    /** At least this many single letters in a row are treated as a spelled-out word. */
    private static final int MIN_SPELLED_LETTERS = 3;
    /** Longest gap of separators allowed between spelled-out letters. */
    private static final int MAX_SEPARATOR_GAP = 3;

    /** A word list entry found in the original text, as original indexes {@code start..end} (inclusive). */
    record Hit(boolean blocking, String entry, int start, int end) {
    }

    private final WordList blocked;
    private final WordList censored;
    private final Set<String> allowed;

    /**
     * @param blocked  words that block the whole message
     * @param censored words that are replaced with stars
     * @param allowed  exceptions; whole words that are never hit, already normalized
     */
    WordFilter(WordList blocked, WordList censored, Set<String> allowed) {
        this.blocked = blocked;
        this.censored = censored;
        this.allowed = Set.copyOf(allowed);
    }

    /** Normalizes allowed-list lines the same way chat is normalized. */
    static Set<String> normalizeAllowed(List<String> lines) {
        Set<String> result = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(TextNormalizer.forFilter(trimmed).text());
            }
        }
        return result;
    }

    /** Every blocked or censored word in {@code original}. */
    List<Hit> scan(String original) {
        TextNormalizer.Normalized normalized = TextNormalizer.forFilter(original);
        List<Hit> hits = new ArrayList<>();
        collect(normalized, true, hits);
        TextNormalizer.Normalized spelled = joinSpelledLetters(normalized);
        if (spelled != null) {
            collect(spelled, false, hits);
        }
        return hits;
    }

    private void collect(TextNormalizer.Normalized text, boolean checkAllowed, List<Hit> hits) {
        for (WordList.Found found : blocked.find(text)) {
            if (!checkAllowed || !isAllowed(text.text(), found)) {
                hits.add(new Hit(true, found.entry().original(), text.originStart()[found.start()], text.originEnd()[found.end()]));
            }
        }
        for (WordList.Found found : censored.find(text)) {
            if (!checkAllowed || !isAllowed(text.text(), found)) {
                hits.add(new Hit(false, found.entry().original(), text.originStart()[found.start()], text.originEnd()[found.end()]));
            }
        }
    }

    /** True if the whole word around the match is in the allowed list. */
    private boolean isAllowed(String text, WordList.Found found) {
        if (allowed.isEmpty()) {
            return false;
        }
        int start = found.start();
        int end = found.end();
        while (start > 0 && TextNormalizer.isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (end + 1 < text.length() && TextNormalizer.isWordChar(text.charAt(end + 1))) {
            end++;
        }
        return allowed.contains(text.substring(start, end + 1));
    }

    /**
     * Builds a text where every run of single letters separated by short gaps ({@code f u c k}) becomes one
     * word ({@code fuck}). Other characters become spaces. Returns {@code null} if there is no such run.
     */
    private static TextNormalizer.Normalized joinSpelledLetters(TextNormalizer.Normalized text) {
        String chars = text.text();
        List<Integer> singles = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) {
            boolean word = TextNormalizer.isWordChar(chars.charAt(i));
            boolean alone = word && (i == 0 || !TextNormalizer.isWordChar(chars.charAt(i - 1)))
                    && (i + 1 >= chars.length() || !TextNormalizer.isWordChar(chars.charAt(i + 1)));
            if (alone) {
                singles.add(i);
            }
        }
        if (singles.size() < MIN_SPELLED_LETTERS) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        List<int[]> origins = new ArrayList<>();
        List<Integer> repeats = new ArrayList<>();
        int runStart = 0;
        for (int k = 1; k <= singles.size(); k++) {
            boolean continues = k < singles.size() && singles.get(k) - singles.get(k - 1) - 1 <= MAX_SEPARATOR_GAP;
            if (continues) {
                continue;
            }
            if (k - runStart >= MIN_SPELLED_LETTERS) {
                if (!out.isEmpty()) {
                    out.append(' ');
                    origins.add(new int[]{0, 0});
                    repeats.add(1);
                }
                for (int r = runStart; r < k; r++) {
                    int index = singles.get(r);
                    char c = chars.charAt(index);
                    int last = out.length() - 1;
                    if (last >= 0 && out.charAt(last) == c && Character.isLetter(c)) {
                        // Collapse repeated letters like the normalizer does ("f u u c k").
                        origins.get(last)[1] = text.originEnd()[index];
                        repeats.set(last, repeats.get(last) + text.repeats()[index]);
                    } else {
                        out.append(c);
                        origins.add(new int[]{text.originStart()[index], text.originEnd()[index]});
                        repeats.add(text.repeats()[index]);
                    }
                }
            }
            runStart = k;
        }
        if (out.isEmpty()) {
            return null;
        }
        int[] starts = new int[origins.size()];
        int[] ends = new int[origins.size()];
        int[] counts = new int[origins.size()];
        for (int i = 0; i < origins.size(); i++) {
            starts[i] = origins.get(i)[0];
            ends[i] = origins.get(i)[1];
            counts[i] = repeats.get(i);
        }
        return new TextNormalizer.Normalized(out.toString(), starts, ends, counts);
    }
}
