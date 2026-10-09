package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The storage used without a database: JSON files for settings, daily text files for the chat log. */
class FileStorageTest {

    @TempDir
    Path dir;

    @Test
    void settingsRoundTrip() throws Exception {
        FileSettingsStore store = new FileSettingsStore(dir);
        UUID player = UUID.randomUUID();
        assertEquals(Optional.empty(), store.load(player));

        PlayerChatSettings settings = new PlayerChatSettings(false, false, true, "staff", Set.of(UUID.randomUUID()), "none");
        store.save(player, settings);

        assertEquals(Optional.of(settings), new FileSettingsStore(dir).load(player));
    }

    @Test
    void chatLogWritesDailyFile() throws Exception {
        FileChatLog log = FileChatLog.create(dir, 30);
        log.log(new ChatLog.Entry(System.currentTimeMillis(), "lobby-1", "global", UUID.randomUUID(), "Steve",
                "سلام دنیا", "salam donya", "sent", ""));
        log.close();

        Path file = dir.resolve("logs").resolve("chat").resolve(LocalDate.now() + ".log");
        String content = Files.readString(file);
        assertTrue(content.contains("[global]") && content.contains("Steve") && content.contains("سلام دنیا"), content);
    }
}
