package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The chat filter: word lists from the {@code filters/} folder plus link and IP detection.
 * Thread-safe and immutable; {@code /lobby reload} builds a new one.
 *
 * <p>Files: every {@code blocked*.txt} blocks the message, every {@code censored*.txt} is replaced with
 * stars, every {@code allowed*.txt} lists exceptions. See {@link WordList} for the line format.
 */
public final class ChatFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatFilter.class);
    /** Folder next to the config files. */
    public static final String FOLDER = "filters";
    /** Files created on first start. */
    private static final List<String> DEFAULT_FILES = List.of(
            "blocked.txt", "censored.txt", "allowed.txt", "blocked-persian.txt");

    /** What kind of problem a finding is; each has its own actions in chat.yml. */
    public enum Category {
        BLOCKED_WORDS("blocked-words"),
        CENSORED_WORDS("censored-words"),
        LINKS("links"),
        IPS("ips");

        private final String configName;

        Category(String configName) {
            this.configName = configName;
        }

        /** Section name in chat.yml, e.g. {@code blocked-words}. */
        public String configName() {
            return configName;
        }
    }

    /**
     * Something the filter found.
     *
     * @param category what kind
     * @param detail   the word list entry or the address found, for staff and logs
     * @param start    first index in the original text, or -1 if it cannot be located (hidden links)
     * @param end      last index in the original text (inclusive), or -1
     */
    public record Finding(Category category, String detail, int start, int end) {
    }

    private final WordFilter words;
    private final LinkDetector links;
    private final int entryCount;

    private ChatFilter(WordFilter words, LinkDetector links, int entryCount) {
        this.words = words;
        this.links = links;
        this.entryCount = entryCount;
    }

    /** A filter without word lists (links and IPs are still detected). */
    public static ChatFilter withoutWordLists(Set<String> allowedDomains) {
        return new ChatFilter(new WordFilter(WordList.empty(), WordList.empty(), Set.of()), new LinkDetector(allowedDomains), 0);
    }

    /** Builds a filter from lines, without files. Used by tests and by {@link #load}. */
    public static ChatFilter of(List<String> blocked, List<String> censored, List<String> allowed, Set<String> allowedDomains) {
        WordList blockedList = WordList.parse(blocked);
        WordList censoredList = WordList.parse(censored);
        return new ChatFilter(new WordFilter(blockedList, censoredList, WordFilter.normalizeAllowed(allowed)),
                new LinkDetector(allowedDomains), blockedList.size() + censoredList.size());
    }

    /**
     * Reads every list in {@code dataDir/filters}, creating the default files first if the folder is new.
     * Reads files, so call it off the tick thread.
     */
    public static ChatFilter load(Path dataDir, Set<String> allowedDomains) throws IOException {
        Path folder = dataDir.resolve(FOLDER);
        createDefaults(folder);
        List<String> blocked = new ArrayList<>();
        List<String> censored = new ArrayList<>();
        List<String> allowed = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.txt")) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (name.startsWith("blocked")) {
                    blocked.addAll(lines);
                } else if (name.startsWith("censored")) {
                    censored.addAll(lines);
                } else if (name.startsWith("allowed")) {
                    allowed.addAll(lines);
                } else {
                    LOGGER.warn("Ignored {}: filter files must start with blocked, censored or allowed", file);
                }
            }
        }
        return of(blocked, censored, allowed, allowedDomains);
    }

    private static void createDefaults(Path folder) throws IOException {
        if (Files.isDirectory(folder)) {
            return;
        }
        Files.createDirectories(folder);
        for (String file : DEFAULT_FILES) {
            try (InputStream in = ChatFilter.class.getResourceAsStream("/defaults/filters/" + file)) {
                if (in != null) {
                    Files.copy(in, folder.resolve(file));
                }
            }
        }
        LOGGER.info("Created the default chat filter lists in {}", folder);
    }

    /** Number of blocked and censored entries, for logs. */
    public int entryCount() {
        return entryCount;
    }

    /** Everything wrong with {@code original}. Empty if the message is fine. */
    public List<Finding> check(String original) {
        List<Finding> findings = new ArrayList<>();
        for (WordFilter.Hit hit : words.scan(original)) {
            findings.add(new Finding(hit.blocking() ? Category.BLOCKED_WORDS : Category.CENSORED_WORDS,
                    hit.entry(), hit.start(), hit.end()));
        }
        for (LinkDetector.Found found : links.find(original)) {
            findings.add(new Finding(found.kind() == LinkDetector.Kind.IP ? Category.IPS : Category.LINKS,
                    found.value(), -1, -1));
        }
        return findings;
    }

    /**
     * Replaces the located findings in {@code original} with {@code mask}, one mask character per original
     * character. Findings without a position are ignored.
     */
    public static String censor(String original, List<Finding> findings, char mask) {
        char[] chars = original.toCharArray();
        for (Finding finding : findings) {
            if (finding.start() < 0) {
                continue;
            }
            for (int i = finding.start(); i <= finding.end() && i < chars.length; i++) {
                if (!Character.isWhitespace(chars[i])) {
                    chars[i] = mask;
                }
            }
        }
        return new String(chars);
    }
}
