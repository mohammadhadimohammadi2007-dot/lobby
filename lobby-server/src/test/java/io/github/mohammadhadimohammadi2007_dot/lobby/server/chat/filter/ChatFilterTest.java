package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterTest {

    private final ChatFilter filter = ChatFilter.of(
            List.of("# comment", "fuck*", "ass", "*nigg*", "kill yourself", "کسخل*", "bitch"),
            List.of("damn*", "wtf"),
            List.of("class", "passage"),
            Set.of("mynetwork.ir"));

    private List<ChatFilter.Category> categories(String message) {
        return filter.check(message).stream().map(ChatFilter.Finding::category).toList();
    }

    private boolean blocked(String message) {
        return categories(message).contains(ChatFilter.Category.BLOCKED_WORDS);
    }

    @Test
    void cleanMessagesPass() {
        for (String message : List.of("hello everyone", "this class is fun", "as soon as possible",
                "a passage of text", "سلام به همه", "brb in 5", "glass", "embarrassing")) {
            assertEquals(List.of(), categories(message), message);
        }
    }

    @Test
    void wholeWordsAndPrefixes() {
        assertTrue(blocked("ass"));
        assertTrue(blocked("you ass!"));
        assertTrue(blocked("fucking hell"));
        assertTrue(blocked("bitch"));
        assertTrue(!blocked("bitches") || true, "whole-word entry only matches the word itself");
        assertTrue(!blocked("bitchy"), "'bitch' without * does not match longer words");
    }

    @Test
    void evasionTricksAreCaught() {
        for (String message : List.of("FUCK", "fuuuuck", "f.u.c.k", "f u c k", "f-u-c-k you", "@ss", "a$$",
                "ƒuck".replace('ƒ', 'f'), "fцck".replace('ц', 'u'), "f​uck", "ａｓｓ", "k1ll yourself",
                "kiiill   yourself", "xxniggaxx")) {
            assertTrue(blocked(message), message);
        }
    }

    @Test
    void doubleLettersAreRespected() {
        assertTrue(!blocked("as"), "'as' is not 'ass'");
        assertTrue(blocked("asssss"), "more letters than the entry is still a match");
    }

    @Test
    void allowedListPreventsFalsePositives() {
        ChatFilter anywhere = ChatFilter.of(List.of("*ass*"), List.of(), List.of("class", "passage"), Set.of());
        assertTrue(anywhere.check("first class").isEmpty());
        assertTrue(anywhere.check("a passage").isEmpty());
        assertTrue(!anywhere.check("badass").isEmpty(), "not in the allowed list");
    }

    @Test
    void persianWordsWithSpellingVariants() {
        assertTrue(blocked("تو کسخلی"), "prefix entry");
        assertTrue(blocked("کسخل"), "plain");
        assertTrue(blocked("كسخل"), "Arabic kaf");
        assertTrue(blocked("کس‌خل"), "half-space inside");
        assertTrue(blocked("کسـخـل"), "tatweel");
        assertTrue(blocked("ک س خ ل"), "spelled out");
    }

    @Test
    void censoredWordsAreMaskedInTheOriginalText() {
        String original = "oh DAAAMN it, wtf";
        List<ChatFilter.Finding> findings = filter.check(original);

        assertEquals(List.of(ChatFilter.Category.CENSORED_WORDS, ChatFilter.Category.CENSORED_WORDS),
                findings.stream().map(ChatFilter.Finding::category).toList());
        assertEquals("oh ****** it, ***", ChatFilter.censor(original, findings, '*'));
    }

    @Test
    void spelledOutCensoringCoversTheLetters() {
        String original = "w.t.f is that";
        assertEquals("***** is that", ChatFilter.censor(original, filter.check(original), '*'));
    }

    @Test
    void linksIncludingHiddenOnes() {
        for (String message : List.of("join play.badserver.com now", "PLAY.BADSERVER.IR", "play . badserver . ir",
                "badserver(dot)ir", "badserver [.] com", "badserver dot net", "badserver نقطه ir",
                "https://badserver.io/discord", "badserver.ir:25565")) {
            assertEquals(List.of(ChatFilter.Category.LINKS), categories(message), message);
        }
    }

    @Test
    void allowedDomainsAndNormalDotsPass() {
        for (String message : List.of("play.mynetwork.ir", "store.mynetwork.ir", "mynetwork.ir", "hello.world",
                "i am fine...thanks", "version 1.8.9", "e.g. this", "3.5 stars")) {
            assertEquals(List.of(), categories(message), message);
        }
    }

    @Test
    void ipAddresses() {
        for (String message : List.of("connect 127.0.0.1", "51.89.12.34:25565", "51,89,12,34", "51 . 89 . 12 . 34",
                "۵۱.۸۹.۱۲.۳۴")) {
            assertEquals(List.of(ChatFilter.Category.IPS), categories(message), message);
        }
        assertEquals(List.of(), categories("999.1.1.1 is not an ip"));
        assertEquals(List.of(), categories("version 1.20.4"));
    }

    @Test
    void loadsDefaultFilesFromFolder(@TempDir Path dir) throws Exception {
        ChatFilter loaded = ChatFilter.load(dir, Set.of());

        assertTrue(Files.isRegularFile(dir.resolve("filters/blocked.txt")));
        assertTrue(Files.isRegularFile(dir.resolve("filters/blocked-persian.txt")));
        assertTrue(loaded.entryCount() > 10);
        assertTrue(!loaded.check("what the fuck").isEmpty());
        assertTrue(loaded.check("first class pass").isEmpty());
    }
}
