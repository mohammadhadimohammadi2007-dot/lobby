package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Map;

/**
 * Turns chat text into a simple form for filtering, so tricks like {@code F.u.C.k}, {@code fuuuck},
 * {@code @ss}, Cyrillic look-alike letters, Arabic {@code ي} instead of Persian {@code ی}, or invisible
 * characters between letters do not get around the word filter.
 *
 * <p>Only used for checking. Players always see the original text (except censored parts), and the
 * original is what gets logged.
 *
 * <p>Every character of the result remembers which characters of the original it came from, so a match
 * in the normalized text can be censored at the right place in the original.
 */
public final class TextNormalizer {

    /** Arabic letters that have a Persian twin, and other spellings that should match the same word. */
    private static final Map<Character, Character> PERSIAN = Map.ofEntries(
            Map.entry('ي', 'ی'), Map.entry('ى', 'ی'), Map.entry('ئ', 'ی'),
            Map.entry('ك', 'ک'),
            Map.entry('ة', 'ه'), Map.entry('ۀ', 'ه'),
            Map.entry('أ', 'ا'), Map.entry('إ', 'ا'), Map.entry('آ', 'ا'), Map.entry('ٱ', 'ا'),
            Map.entry('ؤ', 'و'));

    /** Cyrillic and Greek letters that look like Latin ones. */
    private static final Map<Character, Character> HOMOGLYPHS = Map.ofEntries(
            Map.entry('а', 'a'), Map.entry('в', 'b'), Map.entry('е', 'e'), Map.entry('ё', 'e'), Map.entry('к', 'k'),
            Map.entry('м', 'm'), Map.entry('н', 'h'), Map.entry('о', 'o'), Map.entry('р', 'p'), Map.entry('с', 'c'),
            Map.entry('т', 't'), Map.entry('у', 'y'), Map.entry('х', 'x'), Map.entry('і', 'i'), Map.entry('ј', 'j'),
            Map.entry('ѕ', 's'), Map.entry('ԁ', 'd'), Map.entry('ɡ', 'g'),
            Map.entry('α', 'a'), Map.entry('β', 'b'), Map.entry('ε', 'e'), Map.entry('η', 'n'), Map.entry('ι', 'i'),
            Map.entry('κ', 'k'), Map.entry('ν', 'v'), Map.entry('ο', 'o'), Map.entry('ρ', 'p'), Map.entry('τ', 't'),
            Map.entry('υ', 'u'), Map.entry('χ', 'x'), Map.entry('ω', 'w'));

    /** Digits used as letters. Only applied next to letters, so numbers like "2024" stay numbers. */
    private static final Map<Character, Character> LEET_DIGITS = Map.of(
            '0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's', '7', 't', '8', 'b', '9', 'g');

    /** Symbols that are almost never punctuation: used as letters whenever they touch a letter ("a$$", "@ss"). */
    private static final Map<Character, Character> LEET_LETTER_SYMBOLS = Map.of('@', 'a', '$', 's', '€', 'e');

    /** Symbols that are often punctuation: only used as letters when a letter follows ("sh!t" but not "noob!"). */
    private static final Map<Character, Character> LEET_PUNCTUATION = Map.of('!', 'i', '|', 'i', '+', 't');

    private static final char TATWEEL = 'ـ';

    private TextNormalizer() {
    }

    /**
     * Normalized text.
     *
     * @param text        the normalized characters
     * @param originStart for each character of {@code text}, the index of the first original character it came from
     * @param originEnd   for each character of {@code text}, the index of the last original character it came from
     * @param repeats     for each character of {@code text}, how many identical letters were collapsed into it
     *                    (so {@code ass} is {@code as} with repeats {@code [1, 2]} and does not match the word {@code as})
     */
    public record Normalized(String text, int[] originStart, int[] originEnd, int[] repeats) {
    }

    /**
     * Full normalization for the word filter: Unicode compatibility forms, lower case, Persian spelling,
     * no invisible characters or diacritics, ASCII digits, look-alike letters, leetspeak, and repeated
     * letters collapsed to one ({@code fuuuck} becomes {@code fuck}) with the repeat counts kept in
     * {@link Normalized#repeats()}, and runs of spaces collapsed to one. Word lists are normalized the same way.
     */
    public static Normalized forFilter(String input) {
        Buffer basic = basic(input);
        Buffer leet = leetspeak(basic);
        return collapseRepeats(leet).toNormalized();
    }

    /**
     * Light normalization for link and IP detection: compatibility forms, lower case, ASCII digits and
     * no invisible characters, but no leetspeak (digits must stay digits).
     */
    public static String forLinks(String input) {
        return basic(input).toNormalized().text();
    }

    /** True if {@code c} is a letter or digit after normalization (used for word boundaries). */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static Buffer basic(String input) {
        Buffer out = new Buffer(input.length());
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            int length = Character.charCount(codePoint);
            String compatible = Normalizer.normalize(input.substring(i, i + length), Normalizer.Form.NFKC);
            for (int j = 0; j < compatible.length(); j++) {
                char c = compatible.charAt(j);
                if (shouldDrop(c)) {
                    continue;
                }
                c = Character.isWhitespace(c) ? ' ' : Character.toLowerCase(c);
                c = PERSIAN.getOrDefault(c, c);
                c = HOMOGLYPHS.getOrDefault(c, c);
                if (Character.isDigit(c)) {
                    c = (char) ('0' + Character.digit(c, 10));
                }
                out.add(c, i, i + length - 1);
            }
            i += length;
        }
        return out;
    }

    /** Invisible characters, diacritics and the Arabic stretching character carry no meaning for filtering. */
    private static boolean shouldDrop(char c) {
        int type = Character.getType(c);
        return c == TATWEEL
                || type == Character.FORMAT                // ZWNJ, ZWJ, bidi controls, BOM...
                || type == Character.NON_SPACING_MARK      // Arabic and Latin diacritics
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.CONTROL;
    }

    private static Buffer leetspeak(Buffer in) {
        Buffer out = new Buffer(in.size);
        for (int i = 0; i < in.size; i++) {
            char c = in.chars[i];
            char prev = i > 0 ? in.chars[i - 1] : ' ';
            char next = i + 1 < in.size ? in.chars[i + 1] : ' ';
            Character digit = LEET_DIGITS.get(c);
            if (digit != null && (Character.isLetter(prev) || Character.isLetter(next))
                    && !Character.isDigit(prev) && !Character.isDigit(next)) {
                c = digit;
            } else if (LEET_LETTER_SYMBOLS.containsKey(c)
                    && (Character.isLetter(next) || Character.isLetter(prev) || LEET_LETTER_SYMBOLS.containsKey(prev))
                    && touchesLetter(in, i)) {
                c = LEET_LETTER_SYMBOLS.get(c);
            } else if (LEET_PUNCTUATION.containsKey(c) && Character.isLetter(next)) {
                c = LEET_PUNCTUATION.get(c);
            }
            out.add(c, in.starts[i], in.ends[i]);
        }
        return out;
    }

    /** True if the run of letter-like symbols around {@code index} is attached to a letter. */
    private static boolean touchesLetter(Buffer in, int index) {
        int left = index;
        while (left > 0 && LEET_LETTER_SYMBOLS.containsKey(in.chars[left - 1])) {
            left--;
        }
        int right = index;
        while (right + 1 < in.size && LEET_LETTER_SYMBOLS.containsKey(in.chars[right + 1])) {
            right++;
        }
        return (left > 0 && Character.isLetter(in.chars[left - 1]))
                || (right + 1 < in.size && Character.isLetter(in.chars[right + 1]));
    }

    private static Buffer collapseRepeats(Buffer in) {
        Buffer out = new Buffer(in.size);
        for (int i = 0; i < in.size; i++) {
            char c = in.chars[i];
            if (out.size > 0 && out.chars[out.size - 1] == c && (Character.isLetter(c) || c == ' ')) {
                out.ends[out.size - 1] = in.ends[i];
                out.repeats[out.size - 1]++;
                continue;
            }
            out.add(c, in.starts[i], in.ends[i]);
        }
        return out;
    }

    /** Characters with their origin ranges, grown as needed. */
    private static final class Buffer {
        char[] chars;
        int[] starts;
        int[] ends;
        int[] repeats;
        int size;

        Buffer(int capacity) {
            int initial = Math.max(capacity, 4);
            chars = new char[initial];
            starts = new int[initial];
            ends = new int[initial];
            repeats = new int[initial];
        }

        void add(char c, int start, int end) {
            if (size == chars.length) {
                int grown = size * 2;
                chars = Arrays.copyOf(chars, grown);
                starts = Arrays.copyOf(starts, grown);
                ends = Arrays.copyOf(ends, grown);
                repeats = Arrays.copyOf(repeats, grown);
            }
            chars[size] = c;
            starts[size] = start;
            ends[size] = end;
            repeats[size] = 1;
            size++;
        }

        Normalized toNormalized() {
            return new Normalized(new String(chars, 0, size),
                    Arrays.copyOf(starts, size), Arrays.copyOf(ends, size), Arrays.copyOf(repeats, size));
        }
    }
}
