package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.spam;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.Similarity;

import java.util.Collection;
import java.util.Locale;

/** Small, stateless spam checks used by the anti-spam stage. */
public final class SpamChecks {

    private SpamChecks() {
    }

    /**
     * Share of upper-case letters among cased letters (0-100). Letters without case, such as Persian,
     * are not counted, so Persian messages are never "too loud".
     */
    public static int capsPercent(String text) {
        int cased = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                upper++;
                cased++;
            } else if (Character.isLowerCase(c)) {
                cased++;
            }
        }
        return cased == 0 ? 0 : upper * 100 / cased;
    }

    /** Number of cased letters, used for the minimum length of the caps check. */
    public static int casedLetters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c) || Character.isLowerCase(c)) {
                count++;
            }
        }
        return count;
    }

    /** Lower-cases a too-loud message. */
    public static String quieten(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** Shortens every run of the same character to at most {@code max} ({@code !!!!!!} becomes {@code !!!}). */
    public static String limitRepeats(String text, int max) {
        StringBuilder out = new StringBuilder(text.length());
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            run = i > 0 && text.charAt(i - 1) == c ? run + 1 : 1;
            if (run <= max) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** True if {@code normalized} is at least {@code threshold} similar (0-1) to any of {@code recent}. */
    public static boolean isNearDuplicate(String normalized, Collection<String> recent, double threshold) {
        for (String previous : recent) {
            if (Similarity.ratio(normalized, previous) >= threshold) {
                return true;
            }
        }
        return false;
    }
}
