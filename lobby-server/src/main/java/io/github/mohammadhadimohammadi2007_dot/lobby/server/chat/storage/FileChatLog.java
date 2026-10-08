package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Chat log as daily text files in {@code logs/chat/} (for example {@code 2026-10-08.log}), used when there is
 * no database. Files older than the retention time are deleted.
 */
public final class FileChatLog extends BatchingChatLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String SUFFIX = ".log";

    private final Path folder;
    private final int retentionDays;

    private FileChatLog(Path folder, int retentionDays) {
        this.folder = folder;
        this.retentionDays = retentionDays;
    }

    /** Starts writing to {@code dataDir/logs/chat}. */
    public static FileChatLog create(Path dataDir, int retentionDays) {
        FileChatLog log = new FileChatLog(dataDir.resolve("logs").resolve("chat"), retentionDays);
        log.start();
        return log;
    }

    @Override
    protected void write(List<Entry> batch) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve(LocalDate.now() + SUFFIX);
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (Entry entry : batch) {
                out.write("[" + TIME.format(Instant.ofEpochMilli(entry.timeMillis())) + "] [" + entry.channel() + "] "
                        + entry.senderName() + ": " + entry.original().replace('\n', ' '));
                if (!entry.outcome().equals("sent")) {
                    out.write("  <" + entry.outcome() + (entry.reason().isEmpty() ? "" : ": " + entry.reason()) + ">");
                }
                out.write(System.lineSeparator());
            }
        }
    }

    @Override
    protected void cleanup() throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        LocalDate oldest = LocalDate.now().minusDays(retentionDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*" + SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                try {
                    if (LocalDate.parse(name.substring(0, name.length() - SUFFIX.length())).isBefore(oldest)) {
                        Files.deleteIfExists(file);
                    }
                } catch (DateTimeParseException notOurFile) {
                    // Leave files we did not create alone.
                }
            }
        }
    }

    @Override
    public String describe() {
        return "files in " + folder;
    }
}
