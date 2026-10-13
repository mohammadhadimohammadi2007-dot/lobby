package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PvP records in the table {@code <prefix>pvp_stats} of the shared database, so every lobby counts together. */
public final class DatabasePvpStats implements PvpStatsStore {

    private final DatabasePool database;
    private final String table;

    private DatabasePvpStats(DatabasePool database, String table) {
        this.database = database;
        this.table = table;
    }

    /**
     * Creates the table if needed. Blocking; called once at startup.
     *
     * @param tablePrefix already validated to contain only letters, digits and underscores
     */
    public static DatabasePvpStats create(DatabasePool database, String tablePrefix) throws SQLException {
        DatabasePvpStats store = new DatabasePvpStats(database, tablePrefix + "pvp_stats");
        database.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + store.table + " ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(16) NOT NULL,"
                        + "kills INT NOT NULL,"
                        + "deaths INT NOT NULL,"
                        + "streak INT NOT NULL,"
                        + "best_streak INT NOT NULL,"
                        + "updated BIGINT NOT NULL,"
                        + "INDEX (kills), INDEX (deaths), INDEX (streak), INDEX (best_streak)"
                        + ") DEFAULT CHARSET=utf8mb4");
            }
            return null;
        });
        return store;
    }

    @Override
    public Optional<PvpStats> load(UUID player) throws SQLException {
        return database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name, kills, deaths, streak, best_streak FROM " + table + " WHERE uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? Optional.of(read(row)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void save(PvpStats stats) throws SQLException {
        database.queryNow(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table
                    + " (uuid, name, kills, deaths, streak, best_streak, updated) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE name = VALUES(name), kills = VALUES(kills), deaths = VALUES(deaths),"
                    + " streak = VALUES(streak), best_streak = VALUES(best_streak), updated = VALUES(updated)")) {
                statement.setString(1, stats.id().toString());
                statement.setString(2, stats.name());
                statement.setInt(3, stats.kills());
                statement.setInt(4, stats.deaths());
                statement.setInt(5, stats.streak());
                statement.setInt(6, stats.bestStreak());
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<PvpStats> top(PvpStats.Board board, int limit) throws SQLException {
        return database.queryNow(connection -> {
            // The column comes from the enum, never from outside.
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name, kills, deaths, streak, best_streak FROM " + table + " WHERE " + board.column()
                            + " > 0 ORDER BY " + board.column() + " DESC, name ASC LIMIT ?")) {
                statement.setInt(1, limit);
                List<PvpStats> top = new ArrayList<>();
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        top.add(read(row));
                    }
                }
                return top;
            }
        });
    }

    @Override
    public String describe() {
        return "database table " + table;
    }

    private static PvpStats read(ResultSet row) throws SQLException {
        return new PvpStats(UUID.fromString(row.getString("uuid")), row.getString("name"), row.getInt("kills"),
                row.getInt("deaths"), row.getInt("streak"), row.getInt("best_streak"));
    }
}