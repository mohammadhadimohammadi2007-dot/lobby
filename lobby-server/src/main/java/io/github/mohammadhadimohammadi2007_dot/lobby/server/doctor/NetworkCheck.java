package io.github.mohammadhadimohammadi2007_dot.lobby.server.doctor;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Checks the group and server names the lobby uses against the ones the proxy bridge reports. A name
 * the bridge does not know makes a menu item show "offline" forever, or a portal push everyone back,
 * with no other hint why; this says which name, where it is used, and what the bridge does know.
 *
 * <p>Pure: it only looks at what it is given, so {@code /lobby doctor} can run it any time.
 */
public final class NetworkCheck {

    private static final int MAX_PLACES = 5;

    /**
     * A name the bridge does not know.
     *
     * @param places where it is used, at most a few
     * @param uses   how many places use it in all
     */
    public record Unknown(NetworkReference.Kind kind, String name, List<String> places, int uses) {
    }

    /**
     * What the check found.
     *
     * @param checked false if the bridge has not sent anything yet, so nothing could be checked
     */
    public record Report(boolean checked, List<Unknown> unknown, Set<String> groups, Set<String> servers,
                         int referenceCount) {

        public static final Report NOT_CHECKED = new Report(false, List.of(), Set.of(), Set.of(), 0);

        public boolean ok() {
            return checked && unknown.isEmpty();
        }

        /** One line for {@code /lobby info}. */
        public String summary() {
            if (!checked) {
                return "not checked yet (no network information from the proxy bridge)";
            }
            if (unknown.isEmpty()) {
                return "all " + referenceCount + " group and server names are known to the proxy";
            }
            return unknown.stream().map(u -> u.kind().word() + " '" + u.name() + "' (" + String.join(", ", u.places())
                    + (u.uses() > u.places().size() ? ", ..." : "") + ")").collect(Collectors.joining("; "))
                    + " not known to the proxy; it has the groups " + list(groups);
        }

        /** The console warning, one line per unknown name. */
        public String warning() {
            StringBuilder text = new StringBuilder("These names are not known to the proxy, so what uses them will"
                    + " show offline or fail to connect. Check them against the group and server names in the"
                    + " lobby-bridge config on Velocity:");
            for (Unknown u : unknown) {
                text.append("\n  - ").append(u.kind().word()).append(" '").append(u.name()).append("', used in ")
                        .append(String.join(", ", u.places()));
                if (u.uses() > u.places().size()) {
                    text.append(" and ").append(u.uses() - u.places().size()).append(" more place(s)");
                }
            }
            text.append("\n  The proxy has the groups: ").append(list(groups));
            text.append("\n  and the servers: ").append(list(servers));
            return text.toString();
        }

        private static String list(Set<String> names) {
            return names.isEmpty() ? "(none)" : String.join(", ", names);
        }
    }

    private NetworkCheck() {
    }

    /** Checks {@code references} against what {@code network} reports. */
    public static Report check(List<NetworkReference> references, NetworkState network) {
        if (!network.hasSnapshot()) {
            return Report.NOT_CHECKED;
        }
        Set<String> groups = new TreeSet<>(network.groups().keySet());
        Set<String> servers = network.servers();
        Map<String, List<String>> places = new LinkedHashMap<>();
        Map<String, NetworkReference.Kind> kinds = new LinkedHashMap<>();
        for (NetworkReference reference : references) {
            boolean known = reference.kind() == NetworkReference.Kind.GROUP ? groups.contains(reference.name())
                    : servers.contains(reference.name());
            if (known) {
                continue;
            }
            String key = reference.kind() + ":" + reference.name();
            kinds.put(key, reference.kind());
            places.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference.where());
        }
        List<Unknown> unknown = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : places.entrySet()) {
            List<String> distinct = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            String name = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
            unknown.add(new Unknown(kinds.get(entry.getKey()), name, distinct.subList(0, Math.min(MAX_PLACES, distinct.size())),
                    distinct.size()));
        }
        return new Report(true, unknown, groups, servers, references.size());
    }
}
