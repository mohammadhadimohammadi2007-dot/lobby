package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat settings and the chat log against a real MariaDB. Skipped unless {@code LOBBY_TEST_DB_PORT} is set; the
 * same optional variables as {@code DatabaseReadersIT}. Tables use the prefix {@code lobbytest_} and are dropped.
 */
@EnabledIfEnvironmentVariable(named = "LOBBY_TEST_DB_PORT", matches = "\\d+")
class ChatStorageIT {

    private static final String PREFIX = "lobbytest_";

    private static DatabasePool pool;

    @BeforeAll
    static void connect() throws Exception {
        pool = DatabasePool.connect(new IntegrationsConfig.Database(
                env("LOBBY_TEST_DB_HOST", "localhost"),
                Integer.parseInt(System.getenv("LOBBY_TEST_DB_PORT")),
                env("LOBBY_TEST_DB_NAME", "test"),
                env("LOBBY_TEST_DB_USER", "root"),
                env("LOBBY_TEST_DB_PASSWORD", ""),
                2));
        execute("DROP TABLE IF EXISTS " + PREFIX + "chat_settings, " + PREFIX + "chat_log");
    }

    @AfterAll
    static void dropTables() throws Exception {
        execute("DROP TABLE IF EXISTS " + PREFIX + "chat_settings, " + PREFIX + "chat_log");
        pool.close();
    }

    @Test
    void settingsRoundTrip() throws Exception {
        DatabaseSettingsStore store = DatabaseSettingsStore.create(pool, PREFIX);
        UUID player = UUID.randomUUID();
        Set<UUID> ignored = new LinkedHashSet<>();
        for (int i = 0; i < PlayerChatSettings.MAX_IGNORED; i++) {
            ignored.add(UUID.randomUUID());
        }

        assertEquals(Optional.empty(), store.load(player));

        PlayerChatSettings first = new PlayerChatSettings(false, true, null, "global", ignored);
        store.save(player, first);
        assertEquals(Optional.of(first), store.load(player));

        PlayerChatSettings second = new PlayerChatSettings(true, false, false, null, Set.of());
        store.save(player, second); // Overwrites the same row.
        assertEquals(Optional.of(second), store.load(player));

        // Creating the store again keeps the table and its rows.
        assertEquals(Optional.of(second), DatabaseSettingsStore.create(pool, PREFIX).load(player));
    }

    @Test
    void chatLogWritesBatchesAndCleansUp() throws Exception {
        DatabaseChatLog log = DatabaseChatLog.create(pool, PREFIX, 30);
        UUID sender = UUID.randomUUID();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 450; i++) {
            log.log(new ChatLog.Entry(now, "lobby-1", "local", sender, "Steve", "سلام ❤ " + i, "salam " + i,
                    i % 10 == 0 ? "blocked" : "sent", i % 10 == 0 ? "filter: blocked word" : ""));
        }
        // An old row the cleanup must remove, and an over-long message that must be cut, not rejected.
        log.log(new ChatLog.Entry(now - TimeUnit.DAYS.toMillis(31), "lobby-1", "local", sender, "Steve", "old", "old",
                "sent", ""));
        log.log(new ChatLog.Entry(now, "lobby-1", "local", sender, "AVeryLongPlayerNameIndeed", "x".repeat(5000), "x",
                "sent", ""));
        log.close();

        assertEquals(452, count(""));
        assertEquals(45, count(" WHERE outcome = 'blocked'"));
        assertEquals(1, count(" WHERE message = 'سلام ❤ 7'"));

        log.cleanup();
        assertEquals(451, count(""));
    }

    private static long count(String where) throws SQLException {
        return pool.queryNow(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + PREFIX + "chat_log" + where)) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        });
    }

    private static void execute(String sql) throws SQLException {
        pool.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
