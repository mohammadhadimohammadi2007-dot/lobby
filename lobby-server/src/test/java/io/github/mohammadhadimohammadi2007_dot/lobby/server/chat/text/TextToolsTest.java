package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextToolsTest {

    private static String filter(String text) {
        return TextNormalizer.forFilter(text).text();
    }

    // ---------------- Normalizer ----------------

    @Test
    void lowerCaseAndRepeatedLetters() {
        assertEquals("fuck", filter("FUUUUCK"));
        assertEquals("helo", filter("Hellooo"), "word lists are collapsed the same way, so this still matches");
    }

    @Test
    void leetspeakNextToLetters() {
        assertEquals("as", filter("@ss"), "double s collapsed; the repeat count is kept");
        assertArrayEquals(new int[]{1, 2}, TextNormalizer.forFilter("@ss").repeats());
        assertEquals("shit", filter("sh!t"));
        assertEquals("helo", filter("h3ll0"));
        assertEquals("nob!", filter("noob!"), "a symbol at the end stays punctuation (oo collapses to o)");
        assertEquals("rom 101 in 2024", filter("room 101 in 2024"), "numbers stay numbers (only letters collapse)");
    }

    @Test
    void lookAlikeLettersAndFullWidth() {
        assertEquals(filter("ass"), filter("аss"), "Cyrillic а");
        assertEquals(filter("ass"), filter("ＡＳＳ"), "full-width letters");
    }

    @Test
    void persianSpellingInvisiblesAndDigits() {
        assertEquals("کیک", filter("كيك"), "Arabic kaf and yeh become Persian");
        assertEquals("میخواهم", filter("می‌خواهم"), "ZWNJ removed");
        assertEquals("می", filter("مـــی"), "tatweel removed");
        assertEquals("سلام", filter("سَلام"), "diacritics removed");
        assertEquals("123", filter("۱۲۳"), "Persian digits");
        assertEquals("123", filter("١٢٣"), "Arabic-Indic digits");
        assertEquals("ab", filter("a​b"), "zero-width space");
        assertEquals("ab", filter("a‮b"), "bidi override");
    }

    @Test
    void remembersOriginalPositions() {
        TextNormalizer.Normalized normalized = TextNormalizer.forFilter("fuuuck");
        assertEquals("fuck", normalized.text());
        assertArrayEquals(new int[]{0, 1, 4, 5}, normalized.originStart());
        assertArrayEquals(new int[]{0, 3, 4, 5}, normalized.originEnd());
        assertArrayEquals(new int[]{1, 3, 1, 1}, normalized.repeats());
    }

    @Test
    void linkFormKeepsDigits() {
        assertEquals("play.server1.ir 127.0.0.1", TextNormalizer.forLinks("Play.Server1.IR ۱۲۷.0.0.1"));
    }

    // ---------------- Similarity ----------------

    @Test
    void similarityOfNearDuplicates() {
        assertEquals(1.0, Similarity.ratio("buy now", "buy now"));
        assertTrue(Similarity.ratio("join my server", "join my server!") > 0.9);
        assertTrue(Similarity.ratio("hello", "goodbye") < 0.5);
        assertEquals(3, Similarity.editDistance("kitten", "sitting"));
    }
}
