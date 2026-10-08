package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // ---------------- Persian reshaper ----------------

    @Test
    void shapesSalamWithLamAlefLigature() {
        // سلام = seen (initial) + lam-alef (final ligature) + meem (isolated, alef does not join forward)
        assertEquals("ﺳﻼﻡ", PersianReshaper.shape("سلام"));
        assertEquals("ﻡﻼﺳ", PersianReshaper.reshape("سلام"), "drawn right to left");
    }

    @Test
    void shapesPersianLetters() {
        // پدر: pe initial, dal final, re isolated (dal does not join forward)
        assertEquals("ﭘﺪﺭ", PersianReshaper.shape("پدر"));
        // گچ: gaf initial, che final
        assertEquals("ﮔﭻ", PersianReshaper.shape("گچ"));
        // کی: keheh initial, farsi yeh final
        assertEquals("ﮐﯽ", PersianReshaper.shape("کی"));
        // ژ alone
        assertEquals("ﮊ", PersianReshaper.shape("ژ"));
    }

    @Test
    void zwnjBreaksTheConnectionAndIsRemoved() {
        // می‌خواهم: meem initial, yeh FINAL (ZWNJ stops it), khah initial, waw final,
        // alef isolated, heh initial, meem final
        assertEquals("ﻣﯽﺧﻮﺍﻫﻢ", PersianReshaper.shape("می‌خواهم"));
    }

    @Test
    void diacriticsDoNotBreakJoining() {
        assertEquals(PersianReshaper.shape("سلام"), PersianReshaper.shape("سَلام"));
    }

    @Test
    void mixedLineKeepsEnglishAndNumbersReadable() {
        // Persian first, so the line is right-to-left: Persian on the right, "world 123" left of it.
        assertEquals("world 123 ﻡﻼﺳ", PersianReshaper.reshape("سلام world 123"));
        // English first: left-to-right line, the Persian word is reversed in place.
        assertEquals("hi ﻡﻼﺳ!", PersianReshaper.reshape("hi سلام!"));
    }

    @Test
    void persianDigitsStayInReadingOrder() {
        // "۱۲۳ تا" -> the number keeps its left-to-right order inside the right-to-left line.
        String result = PersianReshaper.reshape("تا ۱۲۳");
        assertTrue(result.startsWith("۱۲۳ "), result);
    }

    @Test
    void bracketsAreMirroredInRightToLeftText() {
        assertEquals("(ﻦﻣ)", PersianReshaper.reshape("(من)"));
    }

    @Test
    void textWithoutPersianIsUnchanged() {
        assertFalse(PersianReshaper.needsReshaping("hello <world> 123"));
        assertEquals("hello (world)", PersianReshaper.reshape("hello (world)"));
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
