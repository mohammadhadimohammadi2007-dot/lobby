package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import java.util.ArrayList;
import java.util.List;

/**
 * A MiniMessage text split into literal parts and {@code %placeholder%} references. Parsed once and reused.
 *
 * <p>The parser remembers whether each placeholder sits inside a MiniMessage tag
 * (for example {@code <click:run_command:'/msg %player_name%'>}). There a component cannot be inserted,
 * so the renderer puts sanitized plain text instead.
 */
final class PlaceholderTemplate {

    /** A piece of the template. */
    sealed interface Piece {
    }

    /** Text copied as it is. */
    record Literal(String text) implements Piece {
    }

    /**
     * A placeholder.
     *
     * @param key       the text between the {@code %} signs, e.g. {@code luckperms_prefix}
     * @param insideTag true if it is inside a MiniMessage tag argument, such as a click command
     * @param tagName   for {@code insideTag}: the tag's name in lower case (e.g. {@code click}); otherwise empty
     */
    record Reference(String key, boolean insideTag, String tagName) implements Piece {

        /** A placeholder in normal text. */
        Reference(String key) {
            this(key, false, "");
        }

        /**
         * True if the placeholder can be inserted as a full component: in normal text, and inside
         * {@code hover} text, which MiniMessage parses like normal text.
         */
        boolean acceptsComponent() {
            return !insideTag || tagName.equals("hover");
        }
    }

    private final List<Piece> pieces;
    private final boolean hasReferences;

    private PlaceholderTemplate(List<Piece> pieces) {
        this.pieces = List.copyOf(pieces);
        this.hasReferences = pieces.stream().anyMatch(piece -> piece instanceof Reference);
    }

    List<Piece> pieces() {
        return pieces;
    }

    boolean hasReferences() {
        return hasReferences;
    }

    /** Splits {@code source} into pieces. Never fails: anything that is not a placeholder stays literal. */
    static PlaceholderTemplate parse(String source) {
        List<Piece> pieces = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean inTag = false;
        int tagStart = -1;
        char quote = 0;
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '%') {
                int end = placeholderEnd(source, i);
                if (end > 0) {
                    if (!literal.isEmpty()) {
                        pieces.add(new Literal(literal.toString()));
                        literal.setLength(0);
                    }
                    pieces.add(new Reference(source.substring(i + 1, end), inTag, inTag ? tagName(source, tagStart) : ""));
                    i = end + 1;
                    continue;
                }
            }
            if (inTag) {
                if (quote != 0) {
                    if (c == '\\' && i + 1 < source.length()) {
                        literal.append(c).append(source.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '>') {
                    inTag = false;
                }
            } else if (c == '\\' && i + 1 < source.length()) {
                // MiniMessage escape such as \<: copy both characters, no tag starts here.
                literal.append(c).append(source.charAt(i + 1));
                i += 2;
                continue;
            } else if (c == '<' && i + 1 < source.length() && isTagStart(source.charAt(i + 1))) {
                inTag = true;
                tagStart = i;
            }
            literal.append(c);
            i++;
        }
        if (!literal.isEmpty()) {
            pieces.add(new Literal(literal.toString()));
        }
        return new PlaceholderTemplate(pieces);
    }

    /** Index of the closing {@code %} if a valid {@code %namespace_params%} starts at {@code start}, else -1. */
    private static int placeholderEnd(String source, int start) {
        int i = start + 1;
        int namespaceLength = 0;
        while (i < source.length() && isNamespaceChar(source.charAt(i))) {
            i++;
            namespaceLength++;
        }
        if (namespaceLength == 0 || i >= source.length() || source.charAt(i) != '_') {
            return -1;
        }
        i++;
        int paramsStart = i;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '%') {
                return i > paramsStart ? i : -1;
            }
            if (Character.isWhitespace(c) || c == '<' || c == '>') {
                return -1;
            }
            i++;
        }
        return -1;
    }

    /** The name of the tag that starts at {@code start} ({@code <click:...>} gives {@code click}). */
    private static String tagName(String source, int start) {
        int i = start + 1;
        StringBuilder name = new StringBuilder();
        while (i < source.length() && source.charAt(i) != ':' && source.charAt(i) != '>') {
            name.append(Character.toLowerCase(source.charAt(i)));
            i++;
        }
        return name.toString();
    }

    private static boolean isNamespaceChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean isTagStart(char c) {
        return Character.isLetter(c) || c == '/' || c == '#' || c == '!' || c == '?' || c == '_';
    }
}
