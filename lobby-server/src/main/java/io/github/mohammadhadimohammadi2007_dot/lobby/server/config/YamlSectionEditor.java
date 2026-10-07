package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes values inside one top-level section of a YAML file while keeping everything else,
 * including comments, exactly as the server owner wrote it.
 *
 * <p>Only handles the simple shape used in our config files: a top-level {@code section:} line
 * followed by indented {@code key: value} lines.
 */
public final class YamlSectionEditor {

    private static final String DEFAULT_INDENT = "  ";

    private YamlSectionEditor() {
    }

    /**
     * Sets {@code values} inside {@code section} of the file and saves it atomically.
     *
     * @param file    the YAML file to change
     * @param section top-level section name, e.g. {@code spawn}
     * @param values  keys and already-formatted values, in the order new keys should be added
     */
    public static void update(Path file, String section, Map<String, String> values) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        String updated = updateText(original, section, values);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, updated, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Same as {@link #update} but works on text. */
    public static String updateText(String yaml, String section, Map<String, String> values) {
        String newline = yaml.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(yaml.split("\r?\n", -1)));

        int header = findSection(lines, section);
        if (header < 0) {
            return appendSection(yaml, newline, section, values);
        }

        int end = sectionEnd(lines, header);
        String indent = DEFAULT_INDENT;
        int lastKeyLine = header;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Pattern keyLine = Pattern.compile("^(\\s+)" + Pattern.quote(entry.getKey())
                    + ":(\\s*)([^#]*?)(\\s+#.*)?$");
            boolean replaced = false;
            for (int i = header + 1; i < end; i++) {
                Matcher matcher = keyLine.matcher(lines.get(i));
                if (matcher.matches()) {
                    indent = matcher.group(1);
                    String comment = matcher.group(4) == null ? "" : matcher.group(4);
                    lines.set(i, indent + entry.getKey() + ": " + entry.getValue() + comment);
                    lastKeyLine = Math.max(lastKeyLine, i);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(lastKeyLine + 1, indent + entry.getKey() + ": " + entry.getValue());
                lastKeyLine++;
                end++;
            }
        }
        return String.join(newline, lines);
    }

    private static int findSection(List<String> lines, String section) {
        Pattern header = Pattern.compile("^" + Pattern.quote(section) + ":\\s*(#.*)?$");
        for (int i = 0; i < lines.size(); i++) {
            if (header.matcher(lines.get(i)).matches()) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the first line after the section: the next line that starts at column 0 with content. */
    private static int sectionEnd(List<String> lines, int header) {
        int lastIndented = header;
        for (int i = header + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (Character.isWhitespace(line.charAt(0))) {
                lastIndented = i;
            } else {
                break;
            }
        }
        return lastIndented + 1;
    }

    private static String appendSection(String yaml, String newline, String section, Map<String, String> values) {
        StringBuilder out = new StringBuilder(yaml);
        if (!yaml.isEmpty() && !yaml.endsWith("\n")) {
            out.append(newline);
        }
        out.append(section).append(':').append(newline);
        values.forEach((key, value) -> out.append(DEFAULT_INDENT).append(key).append(": ").append(value).append(newline));
        return out.toString();
    }
}
