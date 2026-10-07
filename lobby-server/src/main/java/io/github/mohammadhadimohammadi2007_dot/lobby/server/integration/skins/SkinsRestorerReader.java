package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads skins stored by SkinsRestorer (v15 database layout). Read-only.
 *
 * <p>Tables used (with the default prefix {@code sr_}):
 * <ul>
 *   <li>{@code sr_players} - which skin each player chose ({@code skin_identifier}, {@code skin_type})</li>
 *   <li>{@code sr_player_skins} - skins of premium accounts by UUID</li>
 *   <li>{@code sr_custom_skins} - skins saved under a custom name</li>
 *   <li>{@code sr_url_skins} - skins made from an image URL</li>
 * </ul>
 * Phase 3 NPCs use {@link #customSkin} and {@link #premiumSkin}.
 */
public final class SkinsRestorerReader {

    private final DatabasePool database;
    private final String playersTable;
    private final String playerSkinsTable;
    private final String customSkinsTable;
    private final String urlSkinsTable;

    /** @param tablePrefix already validated to contain only letters, digits and underscores */
    public SkinsRestorerReader(DatabasePool database, String tablePrefix) {
        this.database = database;
        this.playersTable = tablePrefix + "players";
        this.playerSkinsTable = tablePrefix + "player_skins";
        this.customSkinsTable = tablePrefix + "custom_skins";
        this.urlSkinsTable = tablePrefix + "url_skins";
    }

    /** Checks that the tables exist, so a wrong prefix gives a clear error at startup. Blocking. */
    public void checkTables() throws SQLException {
        database.queryNow(connection -> {
            for (String table : List.of(playersTable, playerSkinsTable, customSkinsTable, urlSkinsTable)) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1")) {
                    statement.executeQuery().close();
                } catch (SQLException e) {
                    throw new SQLException("table '" + table + "' not found. Is SkinsRestorer using this database"
                            + " (MySQL storage), and does skinsrestorer.table-prefix match its tablePrefix?", e);
                }
            }
            return null;
        });
    }

    /**
     * The skin a player chose with SkinsRestorer ({@code /skin ...}). Empty if they never set one.
     * Blocking: only call from async code.
     */
    public Optional<SkinData> chosenSkinNow(UUID playerId) throws SQLException {
        return database.queryNow(connection -> {
            String identifier;
            String type;
            String variant;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT skin_identifier, skin_type, skin_variant FROM " + playersTable + " WHERE uuid = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    identifier = rows.getString("skin_identifier");
                    type = rows.getString("skin_type");
                    variant = rows.getString("skin_variant");
                }
            }
            if (identifier == null || type == null) {
                return Optional.empty();
            }
            return switch (type) {
                case "PLAYER" -> premiumSkin(connection, identifier);
                case "CUSTOM" -> customSkin(connection, identifier);
                case "URL" -> urlSkin(connection, identifier, variant);
                default -> Optional.empty(); // LEGACY skins are migrated by SkinsRestorer itself.
            };
        });
    }

    /** A skin saved under a custom name (e.g. {@code /sr createcustom}). */
    public CompletableFuture<Optional<SkinData>> customSkin(String name) {
        return database.query(connection -> customSkin(connection, name));
    }

    /** The stored skin of a premium account. */
    public CompletableFuture<Optional<SkinData>> premiumSkin(UUID accountId) {
        return database.query(connection -> premiumSkin(connection, accountId.toString()));
    }

    private Optional<SkinData> premiumSkin(Connection connection, String uuid) throws SQLException {
        return single(connection, "SELECT value, signature FROM " + playerSkinsTable + " WHERE uuid = ?", uuid);
    }

    private Optional<SkinData> customSkin(Connection connection, String name) throws SQLException {
        return single(connection, "SELECT value, signature FROM " + customSkinsTable + " WHERE name = ?", name);
    }

    private Optional<SkinData> urlSkin(Connection connection, String url, String variant) throws SQLException {
        if (variant != null) {
            Optional<SkinData> exact = single(connection,
                    "SELECT value, signature FROM " + urlSkinsTable + " WHERE url = ? AND skin_variant = ?", url, variant);
            if (exact.isPresent()) {
                return exact;
            }
        }
        return single(connection, "SELECT value, signature FROM " + urlSkinsTable + " WHERE url = ? LIMIT 1", url);
    }

    private static Optional<SkinData> single(Connection connection, String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SkinData(rows.getString("value"), rows.getString("signature")));
            }
        }
    }
}
