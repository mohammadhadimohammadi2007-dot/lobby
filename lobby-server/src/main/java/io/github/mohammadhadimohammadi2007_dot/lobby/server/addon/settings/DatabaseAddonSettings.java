package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

/**
 * Addons' per-player values in the table {@code <prefix>addon_settings} of the shared database, so every lobby
 * of the network sees the same values. Created automatically.
 */
public final class DatabaseAddonSettings implements AddonSettingsStore {

    private final DatabasePool database;
    private final String table;

    private DatabaseAddonSettings(DatabasePool database, String table) {
        this.database = database;
        this.table = table;
    }

    /**
     * Creates the table if needed. Blocking; called once at startup.
     *
     * @param tablePrefix already validated to contain only letters, digits and underscores
     */
    public static DatabaseAddonSettings create(DatabasePool database, String tablePrefix) throws SQLException {
        DatabaseAddonSettings store = new DatabaseAddonSettings(database, tablePrefix + "addon_settings");
        database.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + store.table + " ("
                        + "uuid CHAR(36) NOT NULL,"
                        + "addon VARCHAR(64) NOT NULL,"
                        + "setting VARCHAR(64) NOT NULL,"
                        + "value TEXT NOT NULL,"
                        + "updated BIGINT NOT NULL,"
                        + "PRIMARY KEY (uuid, addon, setting)"
                        + ") DEFAULT CHARSET=utf8mb4");
            }
            return null;
        });
        return store;
    }

    @Override
    public Optional<String> load(UUID player, String addon, String key) throws SQLException {
        return database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM " + table + " WHERE uuid = ? AND addon = ? AND setting = ?")) {
                statement.setString(1, player.toString());
                statement.setString(2, addon);
                statement.setString(3, key);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? Optional.of(row.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void save(UUID player, String addon, String key, @Nullable String value) throws SQLException {
        database.queryNow(connection -> {
            if (value == null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE uuid = ? AND addon = ? AND setting = ?")) {
                    statement.setString(1, player.toString());
                    statement.setString(2, addon);
                    statement.setString(3, key);
                    statement.executeUpdate();
                }
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (uuid, addon, setting, value, updated) VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE value = VALUES(value), updated = VALUES(updated)")) {
                statement.setString(1, player.toString());
                statement.setString(2, addon);
                statement.setString(3, key);
                statement.setString(4, value);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public String describe() {
        return "database table " + table;
    }
}
