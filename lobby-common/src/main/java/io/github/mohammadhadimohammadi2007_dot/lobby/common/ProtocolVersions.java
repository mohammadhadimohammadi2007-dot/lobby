package io.github.mohammadhadimohammadi2007_dot.lobby.common;

import java.util.Map;
import java.util.TreeMap;

/**
 * Minecraft protocol version numbers the lobby cares about, and their release names.
 *
 * <p>Names follow ViaVersion's naming (for example {@code 1.8.x} for protocol 47). Full list:
 * <a href="https://minecraft.wiki/w/Protocol_version">minecraft.wiki/w/Protocol_version</a>.
 */
public final class ProtocolVersions {

    /** Minecraft 1.8 to 1.8.9, the oldest client supported through ViaRewind. */
    public static final int V1_8 = 47;

    /** Minecraft 1.16, the first version with RGB colors in chat. */
    public static final int V1_16 = 735;

    /**
     * Minecraft 1.19.4. Clients older than this lack newer display features
     * (for example text display entities), so the lobby treats them as "legacy".
     */
    public static final int V1_19_4 = 762;

    /** Release protocol versions from 1.8 on, copied from ViaVersion 5.12's ProtocolVersion list. */
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
}
