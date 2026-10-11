package io.github.mohammadhadimohammadi2007_dot.lobby.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Timing numbers from tests. They depend on the machine, so they never fail a build: the regular suite
 * asserts counts (renders, packets, objects shown) and only prints these. In the load-test workflow
 * ({@code LOBBY_LOAD_TEST=1}) every number is also appended to {@code build/reports/perf.txt}, with
 * "MISSED" when it is over its target, and the workflow keeps that file.
 */
public final class PerfReport {

    /** Where the load-test workflow collects the numbers. */
    public static final Path FILE = Path.of("build", "reports", "perf.txt");

    private PerfReport() {
    }

    /**
     * Reports one measured value.
     *
     * @param what   what was measured, e.g. "HologramScaleTest: one player joining, extra ms"
     * @param value  the measurement
     * @param target the value it should stay under
     * @param unit   e.g. "ms"
     */
    public static void report(String what, double value, double target, String unit) {
        String line = String.format(Locale.ROOT, "%s: %.1f %s (target < %.0f %s)%s", what, value, unit, target, unit,
                value < target ? "" : "  MISSED");
        System.out.println("[perf] " + line);
        if (!"1".equals(System.getenv("LOBBY_LOAD_TEST"))) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[perf] could not write " + FILE + ": " + e.getMessage());
        }
    }
}
