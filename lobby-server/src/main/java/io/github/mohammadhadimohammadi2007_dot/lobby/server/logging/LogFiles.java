package io.github.mohammadhadimohammadi2007_dot.lobby.server.logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * The server log files, like Paper: {@code logs/latest.log} is the current log; it is archived as
 * {@code logs/<date>-<n>.log.gz} at every start and when the day changes, and archives older than
 * {@code logging.keep-days} are deleted.
 *
 * <p>Only touches files named like an archive directly in {@code logs/}; the chat log in
 * {@code logs/chat/} has its own retention.
 */
public final class LogFiles {

    /** Archives: {@code 2026-10-11-1.log.gz}, and {@code 2026-10-11.log.gz} from older versions. */
    private static final Pattern ARCHIVE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:-(\\d+))?\\.log\\.gz");
    private static final int DEFAULT_KEEP_DAYS = 14;

    private static volatile int keepDays = DEFAULT_KEEP_DAYS;

    private LogFiles() {
    }

    /** How many days of archives to keep, from config.yml. Also deletes what is now too old. */
    public static void keepDays(Path logsDir, int days) {
        keepDays = days;
        deleteOld(logsDir, LocalDate.now(Clock.systemDefaultZone()), days);
    }

    static int keepDays() {
        return keepDays;
    }

    /**
     * Compresses {@code log} into the next free {@code <day>-<n>.log.gz} next to it and empties it.
     * Does nothing if the file is missing or empty.
     *
     * @return the archive, or {@code null} if nothing was archived
     */
    public static Path archive(Path log, LocalDate day) throws IOException {
        if (!Files.isRegularFile(log) || Files.size(log) == 0) {
            return null;
        }
        Path dir = log.toAbsolutePath().getParent();
        Path target = nextFree(dir, day);
        Path temp = dir.resolve(target.getFileName() + ".tmp");
        try (InputStream in = Files.newInputStream(log);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(temp))) {
            in.transferTo(out);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(log);
        return target;
    }

    /** The day a log file was last written, in the server's time zone. */
    static LocalDate lastWritten(Path log) throws IOException {
        return LocalDate.ofInstant(Files.getLastModifiedTime(log).toInstant(), ZoneId.systemDefault());
    }

    /** Deletes archives in {@code logsDir} older than {@code days} days before {@code today}. */
    static void deleteOld(Path logsDir, LocalDate today, int days) {
        if (!Files.isDirectory(logsDir)) {
            return;
        }
        LocalDate oldestKept = today.minusDays(days);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(logsDir, "*.log.gz")) {
            for (Path file : files) {
                Matcher matcher = ARCHIVE.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                try {
                    if (LocalDate.parse(matcher.group(1)).isBefore(oldestKept)) {
                        Files.deleteIfExists(file);
                    }
                } catch (DateTimeParseException | IOException ignored) {
                    // Not one of ours after all, or in use: left alone.
                }
            }
        } catch (IOException ignored) {
            // The logs folder could not be listed; nothing is deleted.
        }
    }

    private static Path nextFree(Path dir, LocalDate day) {
        for (int number = 1; ; number++) {
            Path candidate = dir.resolve(day + "-" + number + ".log.gz");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }
}
