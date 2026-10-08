package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chat log in the table {@code <prefix>chat_log} of the shared database, created automatically.
 * Rows older than the retention time are deleted every few hours.
 */
public final class DatabaseChatLog extends BatchingChatLog {

    private static final int MAX_TEXT = 1024;
    private static final int MAX_REASON = 255;
    /** Rows removed per cleanup query, so a big cleanup never locks the table for long. */
    private static final int CLEANUP_LIMIT = 10_000;

    private final DatabasePool database;
    private final String table;
    private final long retentionMillis;

    private DatabaseChatLog(DatabasePool database, String table, int retentionDays) {
        this.database = database;
        this.table = table;
        this.retentionMillis = TimeUnit.DAYS.toMillis(retentionDays);
    }

    /** Creates the table if needed and starts writing. Blocking; called once at startup. */
    public static DatabaseChatLog create(DatabasePool database, String tablePrefix, int retentionDays) throws SQLException {
        DatabaseChatLog log = new DatabaseChatLog(database, tablePrefix + "chat_log", retentionDays);
        database.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + log.table + " ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                        + "created BIGINT NOT NULL,"
                        + "server VARCHAR(32) NOT NULL,"
                        + "channel VARCHAR(32) NOT NULL,"
                        + "sender_uuid CHAR(36) NOT NULL,"
                        + "sender_name VARCHAR(16) NOT NULL,"
                        + "message TEXT NOT NULL,"
                        + "normalized TEXT NOT NULL,"
                        + "outcome VARCHAR(16) NOT NULL,"
                        + "reason VARCHAR(255) NOT NULL,"
                        + "INDEX idx_created (created),"
                        + "INDEX idx_sender (sender_uuid)"
                        + ") DEFAULT CHARSET=utf8mb4");
            }
            return null;
        });
        log.start();
        return log;
    }

    @Override
    protected void write(List<Entry> batch) throws SQLException {
        database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (created, server, channel, sender_uuid, sender_name, message, normalized, outcome, reason)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Entry entry : batch) {
                    statement.setLong(1, entry.timeMillis());
                    statement.setString(2, cut(entry.server(), 32));
                    statement.setString(3, cut(entry.channel(), 32));
                    statement.setString(4, entry.sender().toString());
                    statement.setString(5, cut(entry.senderName(), 16));
                    statement.setString(6, cut(entry.original(), MAX_TEXT));
                    statement.setString(7, cut(entry.normalized(), MAX_TEXT));
                    statement.setString(8, entry.outcome());
                    statement.setString(9, cut(entry.reason(), MAX_REASON));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    protected void cleanup() throws SQLException {
        long cutoff = System.currentTimeMillis() - retentionMillis;
        database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE created < ? LIMIT " + CLEANUP_LIMIT)) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public String describe() {
        return "database table " + table;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
