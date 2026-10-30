package io.github.mohammadhadimohammadi2007_dot.lobby.common;

import java.util.Map;
import java.util.TreeMap;

/**
 * Minecraft protocol version numbers the lobby cares about, and their release names.
 *
 * <p>One name per protocol version, covering every release that shares it (for example {@code 1.8.x}
 * for protocol 47). Full list:
 * <a href="https://minecraft.wiki/w/Protocol_version">minecraft.wiki/w/Protocol_version</a>.
 */
public final class ProtocolVersions {

    /** Minecraft 1.8 to 1.8.9, the oldest client supported through ViaRewind. */
    public static final int V1_8 = 47;

    /** Minecraft 1.13, the first version without the 16-character limit on team prefixes and suffixes. */
    public static final int V1_13 = 393;

    /** Minecraft 1.16, the first version with RGB colors in chat. */
    public static final int V1_16 = 735;

    /**
     * Minecraft 1.19.4. Clients older than this lack newer display features
     * (for example text display entities), so the lobby treats them as "legacy".
     */
    public static final int V1_19_4 = 762;

    /** Minecraft 1.20.3, the first version that can hide the numbers of the sidebar. */
    public static final int V1_20_3 = 765;

    /** Minecraft 1.20.5, the first version that understands the transfer packet. */
    public static final int V1_20_5 = 766;

    /** Release protocol versions from 1.8 on, from PrismarineJS/minecraft-data's protocolVersions. */
    private static final TreeMap<Integer, String> NAMES = new TreeMap<>(Map.ofEntries(
            Map.entry(47, "1.8.x"),
            Map.entry(107, "1.9"), Map.entry(108, "1.9.1"), Map.entry(109, "1.9.2"), Map.entry(110, "1.9.3-1.9.4"),
            Map.entry(210, "1.10.x"),
            Map.entry(315, "1.11"), Map.entry(316, "1.11.1-1.11.2"),
            Map.entry(335, "1.12"), Map.entry(338, "1.12.1"), Map.entry(340, "1.12.2"),
            Map.entry(393, "1.13"), Map.entry(401, "1.13.1"), Map.entry(404, "1.13.2"),
            Map.entry(477, "1.14"), Map.entry(480, "1.14.1"), Map.entry(485, "1.14.2"), Map.entry(490, "1.14.3"),
            Map.entry(498, "1.14.4"),
            Map.entry(573, "1.15"), Map.entry(575, "1.15.1"), Map.entry(578, "1.15.2"),
            Map.entry(735, "1.16"), Map.entry(736, "1.16.1"), Map.entry(751, "1.16.2"), Map.entry(753, "1.16.3"),
            Map.entry(754, "1.16.4-1.16.5"),
            Map.entry(755, "1.17"), Map.entry(756, "1.17.1"),
            Map.entry(757, "1.18-1.18.1"), Map.entry(758, "1.18.2"),
            Map.entry(759, "1.19"), Map.entry(760, "1.19.1-1.19.2"), Map.entry(761, "1.19.3"), Map.entry(762, "1.19.4"),
            Map.entry(763, "1.20-1.20.1"), Map.entry(764, "1.20.2"), Map.entry(765, "1.20.3-1.20.4"),
            Map.entry(766, "1.20.5-1.20.6"),
            Map.entry(767, "1.21-1.21.1"), Map.entry(768, "1.21.2-1.21.3"), Map.entry(769, "1.21.4"),
            Map.entry(770, "1.21.5"), Map.entry(771, "1.21.6"), Map.entry(772, "1.21.7-1.21.8"),
            Map.entry(773, "1.21.9-1.21.10"), Map.entry(774, "1.21.11"),
            Map.entry(775, "26.1-26.1.2"), Map.entry(776, "26.2"), Map.entry(777, "26.3")));

    private ProtocolVersions() {
    }

    /**
     * The release name of a protocol version, e.g. {@code 1.8.x} for 47.
     * Unknown numbers (snapshots, future versions) return {@code "protocol <number>"}.
     */
    public static String name(int protocolVersion) {
        String name = NAMES.get(protocolVersion);
        return name != null ? name : "protocol " + protocolVersion;
    }

    /**
     * The protocol version of a release name, for options like {@code min-version: 1.9}: the oldest protocol
     * whose releases include it ({@code 1.20.5} and {@code 1.20.6} are both 766). A release between the known
     * ones (a patch that changed nothing) gives the next known one. A plain number is taken as a protocol
     * version.
     *
     * @return the protocol version, or -1 if the text is not a version or is newer than every known one
     */
    public static int fromName(String name) {
        String text = name.strip();
        if (text.matches("\\d{2,5}")) {
            return Integer.parseInt(text);
        }
        int[] wanted = parts(text);
        if (wanted.length == 0) {
            return -1;
        }
        for (Map.Entry<Integer, String> entry : NAMES.entrySet()) {
            String release = entry.getValue();
            String last = release.substring(release.lastIndexOf('-') + 1);
            // "1.8.x" covers every 1.8 release.
            int[] newest = last.endsWith(".x") ? parts(last.substring(0, last.length() - 2) + "." + Integer.MAX_VALUE)
                    : parts(last);
            if (compare(wanted, newest) <= 0) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /** {@code 1.20.5} as {@code [1, 20, 5]}; empty if it is not made of numbers and dots. */
    private static int[] parts(String version) {
        if (!version.matches("\\d+(\\.\\d+){1,3}")) {
            return new int[0];
        }
        String[] pieces = version.split("\\.");
        int[] numbers = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                numbers[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return numbers;
    }

    /** Compares two versions part by part; a missing part counts as 0, so 1.9 equals 1.9.0. */
    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }
}
