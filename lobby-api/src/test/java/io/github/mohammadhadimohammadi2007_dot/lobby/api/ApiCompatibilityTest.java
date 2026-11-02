package io.github.mohammadhadimohammadi2007_dot.lobby.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the promise to addon developers: an addon built for API 1.x runs on every 1.y with y >= x.
 *
 * <p>{@code api/lobby-api.api} records the API of the last version. This test fails as soon as the API differs from
 * it, and says what the difference means. {@code ./gradlew :lobby-api:apiDump} records the new API, but only after
 * the version in {@code lobby-api/build.gradle.kts} was raised as the change needs:
 * <ul>
 *   <li>something removed or changed, or a new method addons must implement: a new major version;</li>
 *   <li>something new: a new minor version, and {@code @Since("x.y")} on it;</li>
 *   <li>{@code @Experimental} parts: anything goes.</li>
 * </ul>
 */
class ApiCompatibilityTest {

    private static final String HEADER = "# lobby-api ";
    private static final String HEADER_END = " - written by ./gradlew :lobby-api:apiDump; do not edit by hand";
    private static final String EXPERIMENTAL = "experimental ";

    /** What an API change does, from the recorded API to the current one. */
    record Changes(List<String> removed, List<String> added, List<String> newMustImplement,
                   List<String> addedWithoutSince) {

        boolean breaking() {
            return !removed.isEmpty() || !newMustImplement.isEmpty();
        }

        boolean none() {
            return removed.isEmpty() && added.isEmpty();
        }
    }

    @Test
    void theApiMatchesTheRecordedApi() throws IOException {
        Path file = apiFile();
        assertTrue(Files.isRegularFile(file), "No " + file + " yet: run ./gradlew :lobby-api:apiDump");
        List<String> recorded = recordedLines(file);
        String recordedVersion = recordedVersion(file);
        List<String> current = lines(ApiSignatures.read());
        if (!recorded.equals(current)) {
            Changes changes = compare(recorded, ApiSignatures.read(), version(recordedVersion));
            fail("The API is not the one recorded in lobby-api/api/lobby-api.api.\n" + describe(changes)
                    + "\nIf this is intended: raise the version in lobby-api/build.gradle.kts and ApiVersion.CURRENT"
                    + " as said above, then run ./gradlew :lobby-api:apiDump and commit the file.");
        }
        assertEquals(ApiVersion.CURRENT, recordedVersion, "The version changed: run ./gradlew :lobby-api:apiDump");
    }

    @Test
    void onlyTheseInterfacesAreImplementedByAddons() throws IOException {
        // Every other interface is @NonExtendable (or sealed). Adding to this list is a promise: such an interface
        // can only get new methods with a default, forever.
        Set<String> expected = Set.of("LobbyAddon", "ActionFactory", "CustomAction", "PlaceholderResolver");
        Set<String> implementable = new LinkedHashSet<>();
        for (ApiSignatures.Signature signature : ApiSignatures.read()) {
            String text = signature.text();
            if (text.contains(" interface") && !text.contains("[lobby implements]") && !text.contains(" sealed ")
                    && !text.contains(" annotation")) {
                String type = text.substring(0, text.indexOf(' '));
                implementable.add(type.substring(type.lastIndexOf('.') + 1));
            }
        }
        assertEquals(expected, implementable, "an interface the lobby implements needs @NonExtendable");
    }

    @Test
    void theRulesTellBreakingChangesFromNewThings() {
        List<String> before = List.of("A method x()", "A method y()", "B interface");
        List<ApiSignatures.Signature> after = List.of(
                new ApiSignatures.Signature("A method x()", false, null),
                new ApiSignatures.Signature("A method z()", false, "1.1"),
                new ApiSignatures.Signature("A method w()", false, null),
                new ApiSignatures.Signature("B interface", false, null),
                new ApiSignatures.Signature("B method v()" + ApiSignatures.MUST_IMPLEMENT, false, "1.1"),
                new ApiSignatures.Signature("C method t()", true, null));
        Changes changes = compare(before, after, new int[] {1, 1});
        assertEquals(List.of("A method y()"), changes.removed());
        assertEquals(List.of("A method z()", "A method w()", "B method v()" + ApiSignatures.MUST_IMPLEMENT),
                changes.added(), "experimental lines are not part of the promise");
        assertEquals(List.of("B method v()" + ApiSignatures.MUST_IMPLEMENT), changes.newMustImplement());
        assertEquals(List.of("A method w()"), changes.addedWithoutSince(), "new in 1.1 but not marked");
        assertTrue(changes.breaking());
    }

    /**
     * Writes the API file. Run by {@code ./gradlew :lobby-api:apiDump} only (the normal test task skips it), and
     * refuses when the version does not fit the changes.
     */
    @Test
    void writeTheApiFile() throws IOException {
        Path file = apiFile();
        List<ApiSignatures.Signature> current = ApiSignatures.read();
        int[] now = version(ApiVersion.CURRENT);
        assertEquals(System.getProperty("lobby.api.version"), ApiVersion.CURRENT,
                "keep ApiVersion.CURRENT equal to the version in lobby-api/build.gradle.kts");
        if (Files.isRegularFile(file)) {
            int[] before = version(recordedVersion(file));
            Changes changes = compare(recordedLines(file), current, now);
            boolean sameMajor = now[0] == before[0];
            if (changes.breaking() && sameMajor && now[0] > 0) {
                fail("These changes break addons, so they need API " + (before[0] + 1) + ".0.0 (or keep the old way,"
                        + " deprecated, until then):\n" + describe(changes));
            }
            List<String> stable = changes.added().stream().filter(line -> !line.startsWith(EXPERIMENTAL)).toList();
            if (!stable.isEmpty() && sameMajor && now[1] == before[1] && now[0] > 0) {
                fail("New API needs a new minor version: raise " + ApiVersion.CURRENT + " to " + now[0] + "."
                        + (before[1] + 1) + ".0 in lobby-api/build.gradle.kts and ApiVersion.CURRENT.\n" + describe(changes));
            }
            if (!changes.addedWithoutSince().isEmpty() && sameMajor && now[0] > 0) {
                fail("Mark what is new with @Since(\"" + now[0] + "." + now[1] + "\"), so addon developers know which"
                        + " version they need:\n  " + String.join("\n  ", changes.addedWithoutSince()));
            }
        }
        List<String> out = new ArrayList<>();
        out.add(HEADER + ApiVersion.CURRENT + HEADER_END);
        out.addAll(lines(current));
        Files.createDirectories(file.getParent());
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    /** What changed between the recorded lines and the current API, which is version {@code now}. */
    static Changes compare(List<String> recorded, List<ApiSignatures.Signature> current, int[] now) {
        Set<String> before = new LinkedHashSet<>(recorded);
        Set<String> after = new LinkedHashSet<>();
        List<String> added = new ArrayList<>();
        List<String> newMustImplement = new ArrayList<>();
        List<String> withoutSince = new ArrayList<>();
        String version = now[0] + "." + now[1];
        for (ApiSignatures.Signature signature : current) {
            String line = line(signature);
            after.add(line);
            if (signature.experimental() || before.contains(line)) {
                continue;
            }
            added.add(line);
            if (line.endsWith(ApiSignatures.MUST_IMPLEMENT)) {
                newMustImplement.add(line);
            }
            if (!version.equals(signature.since())) {
                withoutSince.add(line);
            }
        }
        List<String> removed = before.stream()
                .filter(line -> !line.startsWith(EXPERIMENTAL) && !after.contains(line)).toList();
        return new Changes(removed, added, newMustImplement, withoutSince);
    }

    private static String describe(Changes changes) {
        StringBuilder text = new StringBuilder();
        if (!changes.removed().isEmpty()) {
            text.append("Removed or changed (breaks addons):\n  ").append(String.join("\n  ", changes.removed())).append('\n');
        }
        if (!changes.newMustImplement().isEmpty()) {
            text.append("New methods addons must implement (breaks addons; give them a default instead):\n  ")
                    .append(String.join("\n  ", changes.newMustImplement())).append('\n');
        }
        List<String> plainAdded = changes.added().stream().filter(line -> !changes.newMustImplement().contains(line)).toList();
        if (!plainAdded.isEmpty()) {
            text.append("New (needs a new minor version):\n  ").append(String.join("\n  ", plainAdded)).append('\n');
        }
        return text.toString();
    }

    private static List<String> lines(List<ApiSignatures.Signature> signatures) {
        return signatures.stream().map(ApiCompatibilityTest::line).toList();
    }

    private static String line(ApiSignatures.Signature signature) {
        return (signature.experimental() ? EXPERIMENTAL : "") + signature.text();
    }

    private static Path apiFile() {
        String path = System.getProperty("lobby.api.file");
        assertTrue(path != null, "run through Gradle, which sets lobby.api.file");
        return Path.of(path);
    }

    private static List<String> recordedLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
    }

    private static String recordedVersion(Path file) throws IOException {
        String first = Files.readAllLines(file, StandardCharsets.UTF_8).getFirst();
        assertTrue(first.startsWith(HEADER), "the first line of " + file + " names the version");
        return first.substring(HEADER.length(), first.indexOf(' ', HEADER.length()));
    }

    private static int[] version(String text) {
        String[] parts = text.split("\\.");
        return new int[] {Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0};
    }
}
