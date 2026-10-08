package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text;

import java.text.Bidi;
import java.util.Map;

/**
 * Makes Persian and Arabic text readable in Minecraft.
 *
 * <p>Minecraft draws every character on its own, left to right. Persian then shows as separate letters in
 * the wrong order. This class fixes that in two steps:
 * <ol>
 *   <li><b>Shaping</b>: each letter is replaced by the form it has at its position in the word (isolated,
 *       initial, middle or final), taken from Unicode's Arabic Presentation Forms, including the Persian
 *       letters {@code پ چ ژ ک گ ی}, and {@code لا} becomes one ligature.</li>
 *   <li><b>Ordering</b>: the Unicode bidirectional algorithm ({@link Bidi}) puts right-to-left runs in visual
 *       order, so mixed lines like {@code سلام world ۱۲۳} come out right, with numbers and English left-to-right.</li>
 * </ol>
 * Only used for display. Filtering and logging always use the original text.
 */
public final class PersianReshaper {

    /** Joins only to the letter before it (alef, dal, re, waw...). */
    private static final int RIGHT = 1;
    /** Joins to both sides (be, sin, kaf, ye...). */
    private static final int DUAL = 2;

    private static final int ISOLATED = 0;
    private static final int FINAL = 1;
    private static final int INITIAL = 2;
    private static final int MEDIAL = 3;

    private static final char LAM = 'ل';
    private static final char TATWEEL = 'ـ';

    /** Letter -> {isolated, final, initial, medial}. Right-joining letters have only the first two. */
    private static final Map<Character, char[]> FORMS = Map.ofEntries(
            Map.entry('ء', new char[]{'ﺀ'}),
            Map.entry('آ', new char[]{'ﺁ', 'ﺂ'}),
            Map.entry('أ', new char[]{'ﺃ', 'ﺄ'}),
            Map.entry('ؤ', new char[]{'ﺅ', 'ﺆ'}),
            Map.entry('إ', new char[]{'ﺇ', 'ﺈ'}),
            Map.entry('ئ', new char[]{'ﺉ', 'ﺊ', 'ﺋ', 'ﺌ'}),
            Map.entry('ا', new char[]{'ﺍ', 'ﺎ'}),
            Map.entry('ب', new char[]{'ﺏ', 'ﺐ', 'ﺑ', 'ﺒ'}),
            Map.entry('ة', new char[]{'ﺓ', 'ﺔ'}),
            Map.entry('ت', new char[]{'ﺕ', 'ﺖ', 'ﺗ', 'ﺘ'}),
            Map.entry('ث', new char[]{'ﺙ', 'ﺚ', 'ﺛ', 'ﺜ'}),
            Map.entry('ج', new char[]{'ﺝ', 'ﺞ', 'ﺟ', 'ﺠ'}),
            Map.entry('ح', new char[]{'ﺡ', 'ﺢ', 'ﺣ', 'ﺤ'}),
            Map.entry('خ', new char[]{'ﺥ', 'ﺦ', 'ﺧ', 'ﺨ'}),
            Map.entry('د', new char[]{'ﺩ', 'ﺪ'}),
            Map.entry('ذ', new char[]{'ﺫ', 'ﺬ'}),
            Map.entry('ر', new char[]{'ﺭ', 'ﺮ'}),
            Map.entry('ز', new char[]{'ﺯ', 'ﺰ'}),
            Map.entry('س', new char[]{'ﺱ', 'ﺲ', 'ﺳ', 'ﺴ'}),
            Map.entry('ش', new char[]{'ﺵ', 'ﺶ', 'ﺷ', 'ﺸ'}),
            Map.entry('ص', new char[]{'ﺹ', 'ﺺ', 'ﺻ', 'ﺼ'}),
            Map.entry('ض', new char[]{'ﺽ', 'ﺾ', 'ﺿ', 'ﻀ'}),
            Map.entry('ط', new char[]{'ﻁ', 'ﻂ', 'ﻃ', 'ﻄ'}),
            Map.entry('ظ', new char[]{'ﻅ', 'ﻆ', 'ﻇ', 'ﻈ'}),
            Map.entry('ع', new char[]{'ﻉ', 'ﻊ', 'ﻋ', 'ﻌ'}),
            Map.entry('غ', new char[]{'ﻍ', 'ﻎ', 'ﻏ', 'ﻐ'}),
            Map.entry('ف', new char[]{'ﻑ', 'ﻒ', 'ﻓ', 'ﻔ'}),
            Map.entry('ق', new char[]{'ﻕ', 'ﻖ', 'ﻗ', 'ﻘ'}),
            Map.entry('ك', new char[]{'ﻙ', 'ﻚ', 'ﻛ', 'ﻜ'}),
            Map.entry('ل', new char[]{'ﻝ', 'ﻞ', 'ﻟ', 'ﻠ'}),
            Map.entry('م', new char[]{'ﻡ', 'ﻢ', 'ﻣ', 'ﻤ'}),
            Map.entry('ن', new char[]{'ﻥ', 'ﻦ', 'ﻧ', 'ﻨ'}),
            Map.entry('ه', new char[]{'ﻩ', 'ﻪ', 'ﻫ', 'ﻬ'}),
            Map.entry('و', new char[]{'ﻭ', 'ﻮ'}),
            Map.entry('ى', new char[]{'ﻯ', 'ﻰ', 'ﯨ', 'ﯩ'}),
            Map.entry('ي', new char[]{'ﻱ', 'ﻲ', 'ﻳ', 'ﻴ'}),
            // Persian letters (Arabic Presentation Forms-A)
            Map.entry('پ', new char[]{'ﭖ', 'ﭗ', 'ﭘ', 'ﭙ'}),
            Map.entry('چ', new char[]{'ﭺ', 'ﭻ', 'ﭼ', 'ﭽ'}),
            Map.entry('ژ', new char[]{'ﮊ', 'ﮋ'}),
            Map.entry('ک', new char[]{'ﮎ', 'ﮏ', 'ﮐ', 'ﮑ'}),
            Map.entry('گ', new char[]{'ﮒ', 'ﮓ', 'ﮔ', 'ﮕ'}),
            Map.entry('ی', new char[]{'ﯼ', 'ﯽ', 'ﯾ', 'ﯿ'}),
            Map.entry('ۀ', new char[]{'ﮤ', 'ﮥ'}));

    /** Lam + alef ligatures: alef variant -> {isolated, final}. */
    private static final Map<Character, char[]> LAM_ALEF = Map.of(
            'ا', new char[]{'ﻻ', 'ﻼ'},
            'آ', new char[]{'ﻵ', 'ﻶ'},
            'أ', new char[]{'ﻷ', 'ﻸ'},
            'إ', new char[]{'ﻹ', 'ﻺ'});

    /** Brackets swap direction inside right-to-left runs. */
    private static final Map<Character, Character> MIRROR = Map.of(
            '(', ')', ')', '(', '[', ']', ']', '[', '{', '}', '}', '{', '<', '>', '>', '<', '«', '»', '»', '«');

    private PersianReshaper() {
    }

    /** True if the text contains Arabic-script letters and therefore needs reshaping. */
    public static boolean needsReshaping(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '؀' && c <= 'ۿ') {
                return true;
            }
        }
        return false;
    }

    /** Shapes and orders {@code text} for display. Text without Arabic-script letters is returned unchanged. */
    public static String reshape(String text) {
        if (!needsReshaping(text)) {
            return text;
        }
        return removeDirectionMarks(visualOrder(shape(text)));
    }

    /** Direction marks guide the ordering step but would show as boxes in Minecraft. */
    private static String removeDirectionMarks(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean mark = c == '‎' || c == '‏' || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩');
            if (!mark) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Step 1: joins letters (logical order is kept). Diacritics and invisible joiners are removed. */
    static String shape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (isTransparent(c)) {
                continue; // Diacritics cannot be positioned by Minecraft; ZWNJ/ZWJ have done their job.
            }
            int joining = joiningType(c);
            boolean joinsBefore = joining != 0 && joinsToNext(previousLetter(text, i));
            if (c == LAM) {
                int next = nextLetterIndex(text, i);
                if (next >= 0 && LAM_ALEF.containsKey(text.charAt(next))) {
                    out.append(LAM_ALEF.get(text.charAt(next))[joinsBefore ? FINAL : ISOLATED]);
                    i = next;
                    continue;
                }
            }
            char[] forms = FORMS.get(c);
            if (forms == null) {
                out.append(c);
                continue;
            }
            boolean joinsAfter = joining == DUAL && forms.length == 4 && joinsFromPrevious(nextLetter(text, i));
            int form = joinsBefore && joinsAfter ? MEDIAL : joinsBefore ? FINAL : joinsAfter ? INITIAL : ISOLATED;
            // Right-joining letters never get INITIAL/MEDIAL: joinsAfter is false for them.
            out.append(forms[form]);
        }
        return out.toString();
    }

    /** Step 2: puts right-to-left runs in the order they must be drawn left to right. */
    static String visualOrder(String text) {
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        if (bidi.isLeftToRight()) {
            return text;
        }
        int runCount = bidi.getRunCount();
        byte[] levels = new byte[runCount];
        Integer[] runs = new Integer[runCount];
        for (int run = 0; run < runCount; run++) {
            levels[run] = (byte) bidi.getRunLevel(run);
            runs[run] = run;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, runCount);
        StringBuilder out = new StringBuilder(text.length());
        for (Integer run : runs) {
            int start = bidi.getRunStart(run);
            int limit = bidi.getRunLimit(run);
            if ((bidi.getRunLevel(run) & 1) == 1) {
                for (int i = limit - 1; i >= start; i--) {
                    char c = text.charAt(i);
                    out.append(MIRROR.getOrDefault(c, c));
                }
            } else {
                out.append(text, start, limit);
            }
        }
        return out.toString();
    }

    private static int joiningType(char c) {
        if (c == TATWEEL) {
            return DUAL;
        }
        char[] forms = FORMS.get(c);
        if (forms == null) {
            return 0;
        }
        return forms.length == 4 ? DUAL : forms.length == 2 ? RIGHT : 0;
    }

    /** True if {@code c} connects to the letter after it (only dual-joining letters do). */
    private static boolean joinsToNext(char c) {
        return joiningType(c) == DUAL;
    }

    /** True if {@code c} accepts a connection from the letter before it. */
    private static boolean joinsFromPrevious(char c) {
        return joiningType(c) != 0;
    }

    /** Characters dropped while shaping: diacritics, and the zero-width (non-)joiners once they have done their job. */
    private static boolean isTransparent(char c) {
        return Character.getType(c) == Character.NON_SPACING_MARK || c == '‍' || c == '‌';
    }

    private static char previousLetter(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '‌') {
                return ' ';
            }
            if (Character.getType(c) != Character.NON_SPACING_MARK && c != '‍') {
                return c;
            }
        }
        return ' ';
    }

    private static int nextLetterIndex(String text, int index) {
        for (int i = index + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '‌') {
                return -1;
            }
            if (Character.getType(c) != Character.NON_SPACING_MARK && c != '‍') {
                return i;
            }
        }
        return -1;
    }

    private static char nextLetter(String text, int index) {
        int next = nextLetterIndex(text, index);
        return next < 0 ? ' ' : text.charAt(next);
    }
}
