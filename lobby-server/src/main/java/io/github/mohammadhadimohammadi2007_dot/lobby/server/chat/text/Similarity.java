package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text;

/** How alike two texts are, for catching repeated messages that differ by a letter or two. */
public final class Similarity {

    private Similarity() {
    }

    /**
     * A value from 0 (nothing in common) to 1 (identical), based on the edit distance: the number of
     * single-character insertions, deletions or replacements needed to turn one text into the other.
     */
    public static double ratio(String a, String b) {
        if (a.equals(b)) {
            return 1;
        }
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 1;
        }
        return 1 - (double) editDistance(a, b) / longest;
    }

    /** Levenshtein distance with two rows of memory. */
    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
