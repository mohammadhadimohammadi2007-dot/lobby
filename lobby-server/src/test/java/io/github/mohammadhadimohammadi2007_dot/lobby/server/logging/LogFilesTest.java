package io.github.mohammadhadimohammadi2007_dot.lobby.server.logging;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** latest.log archives, their numbering and retention; console colors. */
class LogFilesTest {

    @TempDir
    Path logs;

    private static final LocalDate DAY = LocalDate.of(2026, 10, 11);

    @Test
    void archivesAreNumberedPerDayAndCompressed() throws Exception {
        Path latest = logs.resolve("latest.log");
        Files.writeString(latest, "first run\n");
        assertEquals(logs.resolve("2026-10-11-1.log.gz"), LogFiles.archive(latest, DAY));
        assertFalse(Files.exists(latest), "latest.log starts empty again");

        Files.writeString(latest, "second run\n");
        Path second = LogFiles.archive(latest, DAY);
        assertEquals(logs.resolve("2026-10-11-2.log.gz"), second);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(second))) {
            assertEquals("second run\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void anEmptyOrMissingLogIsNotArchived() throws Exception {
        Path latest = logs.resolve("latest.log");
        assertNull(LogFiles.archive(latest, DAY));
        Files.writeString(latest, "");
        assertNull(LogFiles.archive(latest, DAY));
    }

    @Test
    void onlyOldArchivesAreDeleted() throws Exception {
        Path old = Files.writeString(logs.resolve("2026-09-01-1.log.gz"), "x");
        Path oldOldStyle = Files.writeString(logs.resolve("2026-09-02.log.gz"), "x");
        Path recent = Files.writeString(logs.resolve("2026-10-05-3.log.gz"), "x");
        Path notOurs = Files.writeString(logs.resolve("backup.log.gz"), "x");
        Files.createDirectories(logs.resolve("chat"));
        Path chat = Files.writeString(logs.resolve("chat").resolve("2026-01-01.log"), "x");

        LogFiles.deleteOld(logs, DAY, 14);

        assertFalse(Files.exists(old));
        assertFalse(Files.exists(oldOldStyle), "archives of older versions count too");
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(notOurs));
        assertTrue(Files.exists(chat), "the chat log has its own retention");
    }

    @Test
    void colorsOnlyInARealTerminalUnlessForced() {
        assertTrue(ConsoleAppender.useColors(ConsoleAppender.Colors.AUTO, true));
        assertFalse(ConsoleAppender.useColors(ConsoleAppender.Colors.AUTO, false), "Pterodactyl, Docker, pipes");
        assertTrue(ConsoleAppender.useColors(ConsoleAppender.Colors.ON, false));
        assertFalse(ConsoleAppender.useColors(ConsoleAppender.Colors.OFF, true));
        assertEquals(ConsoleAppender.Colors.OFF, ConsoleAppender.parseColors(" Off "));
        assertNull(ConsoleAppender.parseColors("rainbow"));
    }

    @Test
    void colorCodesAreRemovedForPlainOutput() {
        assertEquals("Lobby info Mode: standalone",
                ConsoleAppender.stripAnsi("\u001B[1m\u001B[38;5;3mLobby info\u001B[0m \u001B[38;5;7mMode: standalone"));
        assertEquals("no colors", ConsoleAppender.stripAnsi("no colors"));
    }

    @Test
    void theLineBreakStaysOutsideTheColor() {
        String line = "[12:00:00 WARN ] careful" + System.lineSeparator();
        String colored = ConsoleAppender.colored(line, Level.WARN);
        assertTrue(colored.endsWith("\u001B[0m" + System.lineSeparator()), colored);
        assertEquals(line, ConsoleAppender.colored(line, Level.INFO), "info stays plain");
    }
}
