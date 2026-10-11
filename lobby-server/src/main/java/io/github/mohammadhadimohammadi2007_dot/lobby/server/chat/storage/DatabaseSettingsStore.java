package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Player chat settings in the table {@code <prefix>chat_settings} of the shared database, created automatically. */
public final class DatabaseSettingsStore implements SettingsStore {

    private final DatabasePool database;
    private final String table;

    private DatabaseSettingsStore(DatabasePool database, String table) {
        this.database = database;
        this.table = table;
    }

    /**
     * Creates the table if needed. Blocking; called once at startup.
     *
     * @param tablePrefix already validated to contain only letters, digits and underscores
     */
    public static DatabaseSettingsStore create(DatabasePool database, String tablePrefix) throws SQLException {
        DatabaseSettingsStore store = new DatabaseSettingsStore(database, tablePrefix + "chat_settings");
        database.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + store.table + " ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                        + "chat_visible BOOLEAN NOT NULL,"
                        + "mentions BOOLEAN NOT NULL,"
                        + "ignored TEXT NOT NULL,"
                        + "updated BIGINT NOT NULL,"
                        + "visibility VARCHAR(8) NULL"
                        + ") DEFAULT CHARSET=utf8mb4");
                // Tables made before the visibility setting existed get the column now. Older tables may still
                // have "persian" and "channel" columns from removed settings: they are nullable and left unused.
                statement.execute("ALTER TABLE " + store.table + " ADD COLUMN IF NOT EXISTS visibility VARCHAR(8) NULL");
            }
            return null;
        });
        return store;
    }

    @Override
    public Optional<PlayerChatSettings> load(UUID player) throws SQLException {
        return database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT chat_visible, mentions, ignored, visibility FROM " + table + " WHERE uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PlayerChatSettings(row.getBoolean("chat_visible"), row.getBoolean("mentions"),
                            parseIgnored(row.getString("ignored")),
                            row.getString("visibility")));
                }
            }
        });
    }

    @Override
    public void save(UUID player, PlayerChatSettings settings) throws SQLException {
        database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (uuid, chat_visible, mentions, ignored, updated, visibility)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE chat_visible = VALUES(chat_visible), mentions = VALUES(mentions),"
                    + " ignored = VALUES(ignored),"
                    + " updated = VALUES(updated), visibility = VALUES(visibility)")) {
                statement.setString(1, player.toString());
                statement.setBoolean(2, settings.chatVisible());
                statement.setBoolean(3, settings.mentions());
                statement.setString(4, String.join(",", settings.ignored().stream().map(UUID::toString).toList()));
                statement.setLong(5, System.currentTimeMillis());
                statement.setString(6, settings.visibility());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public String describe() {
        return "database table " + table;
    }

    private static Set<UUID> parseIgnored(String text) {
        Set<UUID> ignored = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return ignored;
        }
        for (String part : text.split(",")) {
            try {
                ignored.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignoredBadEntry) {
                // Skip anything that is not a UUID.
            }
        }
        return ignored;
    }
}
