package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.filter;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.text.TextNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds website addresses and IP addresses in chat, including hidden ones such as
 * {@code play . server . ir}, {@code server(dot)ir}, {@code server نقطه ir} or {@code 127,0,0,1:25565}.
 * Domains in the allowlist (your own) are ignored, including their subdomains.
 */
final class LinkDetector {

    /**
     * Top-level domains that are checked. Ordinary English words (world, live, fun, shop...) are left out on
     * purpose, so chat like "hello.world" or "that was fun.live" does not count as a link.
     */
    private static final String TLDS = "com|net|org|info|biz|io|co|me|gg|xyz|tk|ml|ga|cf|gq|ir|ru|uk|de|fr|nl|eu|us|ca"
            + "|tv|cc|pw|site|online|dev|app|tr|ae|pl|es|it|br|cn|jp|kr|au|ws|su|mx|nu|ly|sh|ai";

    private static final Pattern DOMAIN = Pattern.compile(
            "(?<![a-z0-9-])((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:" + TLDS + "))(?::\\d{1,5})?(?![a-z0-9-])");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(\\d{1,3})[.,](\\d{1,3})[.,](\\d{1,3})[.,](\\d{1,3})(?::\\d{1,5})?(?![0-9])");

    /** "(dot)", "[.]", " dot ", " نقطه " and spaces around dots all mean ".". */
    private static final Pattern BRACKET_DOT = Pattern.compile("\\s*[(\\[{]\\s*(?:dot|\\.|نقطه|دات)\\s*[)\\]}]\\s*");
    private static final Pattern WORD_DOT = Pattern.compile("\\s+(?:dot|نقطه|دات)\\s+");
    private static final Pattern SPACED_DOT = Pattern.compile("\\s*\\.\\s*");
    private static final int MAX_OCTET = 255;

    /** What was found. */
    enum Kind { LINK, IP }

    /** A link or IP found in the text. */
    record Found(Kind kind, String value) {
    }

    private final Set<String> allowedDomains;

    /** @param allowedDomains domains that may be posted, e.g. {@code mynetwork.ir} */
    LinkDetector(Set<String> allowedDomains) {
        this.allowedDomains = allowedDomains.stream().map(d -> d.toLowerCase(Locale.ROOT).strip()).collect(Collectors.toSet());
    }

    /** Every link and IP address in {@code original}. */
    List<Found> find(String original) {
        String text = deobfuscate(TextNormalizer.forLinks(original));
        List<Found> found = new ArrayList<>();
        Matcher ip = IPV4.matcher(text);
        while (ip.find()) {
            if (validOctets(ip)) {
                found.add(new Found(Kind.IP, ip.group()));
            }
        }
        Matcher domain = DOMAIN.matcher(text);
        while (domain.find()) {
            String name = domain.group(1);
            if (!isAllowed(name)) {
                found.add(new Found(Kind.LINK, domain.group()));
            }
        }
        return found;
    }

    /** Puts hidden dots back: {@code play . server (dot) ir} becomes {@code play.server.ir}. */
    static String deobfuscate(String text) {
        String result = BRACKET_DOT.matcher(text).replaceAll(".");
        result = WORD_DOT.matcher(result).replaceAll(".");
        return SPACED_DOT.matcher(result).replaceAll(".");
    }

    private boolean isAllowed(String domain) {
        for (String allowed : allowedDomains) {
            if (domain.equals(allowed) || domain.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean validOctets(Matcher ip) {
        for (int group = 1; group <= 4; group++) {
            if (Integer.parseInt(ip.group(group)) > MAX_OCTET) {
                return false;
            }
        }
        return true;
    }
}
