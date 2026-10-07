package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only SQL against the LiteBans tables ({@code <prefix>bans} and {@code <prefix>mutes}).
 *
 * <p>A punishment counts as active the same way LiteBans' own web interface decides it:
 * {@code active = 1 AND (until < 1 OR until > now)}. Scope: global ({@code *}) punishments and those
 * for {@code serverName} apply.
 */
final class LiteBansQueries {

    /** Most rows read per poll, so a burst of new mutes cannot stall a poll. */
    private static final int MAX_NEW_ROWS = 500;
    private static final String GLOBAL_SCOPE = "*";

    private final String bansTable;
    private final String mutesTable;
    private final String serverName;

    /** @param tablePrefix already validated to contain only letters, digits and underscores */
    LiteBansQueries(String tablePrefix, String serverName) {
        this.bansTable = tablePrefix + "bans";
        this.mutesTable = tablePrefix + "mutes";
        this.serverName = serverName;
    }

    /** A row from the mutes table, with the fields needed to match it to online players. */
    record MuteRow(Punishment punishment, String uuid, String ip) {
    }

    private static String activeCondition() {
        return "active = 1 AND (until < 1 OR until > ?)"
                + " AND (server_scope IS NULL OR server_scope = '' OR server_scope = ? OR server_scope = ?)";
    }

    private int bindActive(PreparedStatement statement, int index, long nowMillis) throws SQLException {
        statement.setLong(index++, nowMillis);
        statement.setString(index++, GLOBAL_SCOPE);
        statement.setString(index++, serverName);
        return index;
    }

    /** Checks that both tables exist, so a wrong prefix gives a clear error at startup. */
    void checkTables(Connection connection) throws SQLException {
        for (String table : List.of(bansTable, mutesTable)) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table + " LIMIT 1")) {
                statement.executeQuery().close();
            } catch (SQLException e) {
                throw new SQLException("table '" + table + "' not found. Is LiteBans using this database,"
                        + " and does litebans.table-prefix match LiteBans' table_prefix?", e);
            }
        }
    }

    Optional<Punishment> activeBan(Connection connection, UUID playerId, String ip, long nowMillis) throws SQLException {
        return activeIn(connection, bansTable, playerId, ip, nowMillis);
    }

    Optional<Punishment> activeMute(Connection connection, UUID playerId, String ip, long nowMillis) throws SQLException {
        return activeIn(connection, mutesTable, playerId, ip, nowMillis);
    }

    private Optional<Punishment> activeIn(Connection connection, String table, UUID playerId, String ip, long nowMillis)
            throws SQLException {
        String sql = "SELECT id, reason, banned_by_name, until, ipban FROM " + table
                + " WHERE " + activeCondition() + " AND (uuid = ? OR (ipban = 1 AND ip = ?))"
                + " ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindActive(statement, 1, nowMillis);
            statement.setString(index++, playerId.toString());
            statement.setString(index, ip);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(punishment(rows)) : Optional.empty();
            }
        }
    }

    /** Highest mute id so far; polling starts after it. */
    long latestMuteId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM " + mutesTable);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }

    /** Active mutes created after {@code afterId}, oldest first. */
    List<MuteRow> newMutes(Connection connection, long afterId, long nowMillis) throws SQLException {
        String sql = "SELECT id, uuid, ip, reason, banned_by_name, until, ipban FROM " + mutesTable
                + " WHERE id > ? AND " + activeCondition() + " ORDER BY id LIMIT " + MAX_NEW_ROWS;
        List<MuteRow> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, afterId);
            bindActive(statement, 2, nowMillis);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MuteRow(punishment(rows), rows.getString("uuid"), rows.getString("ip")));
                }
            }
        }
        return result;
    }

    /** Which of {@code ids} are still active mutes (not removed, not expired). */
    Set<Long> stillActiveMutes(Connection connection, Collection<Long> ids, long nowMillis) throws SQLException {
        Set<Long> active = new HashSet<>();
        if (ids.isEmpty()) {
            return active;
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id FROM " + mutesTable + " WHERE id IN (" + placeholders + ") AND " + activeCondition();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (long id : ids) {
                statement.setLong(index++, id);
            }
            bindActive(statement, index, nowMillis);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    active.add(rows.getLong(1));
                }
            }
        }
        return active;
    }

    private static Punishment punishment(ResultSet row) throws SQLException {
        String reason = row.getString("reason");
        String by = row.getString("banned_by_name");
        return new Punishment(
                row.getLong("id"),
                reason == null ? "" : reason,
                by == null ? "Console" : by,
                row.getLong("until"),
                row.getBoolean("ipban"));
    }
}
