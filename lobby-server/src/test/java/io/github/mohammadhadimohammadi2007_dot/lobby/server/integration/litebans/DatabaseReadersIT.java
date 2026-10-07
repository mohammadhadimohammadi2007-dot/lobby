package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinsRestorerReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the LiteBans and SkinsRestorer queries against a real MariaDB.
 *
 * <p>Skipped unless {@code LOBBY_TEST_DB_PORT} is set. Optional: {@code LOBBY_TEST_DB_HOST} (default localhost),
 * {@code LOBBY_TEST_DB_NAME} (default test), {@code LOBBY_TEST_DB_USER} (default root),
 * {@code LOBBY_TEST_DB_PASSWORD}. Tables use the prefix {@code lobbytest_} and are dropped afterwards.
 * The table layouts copy LiteBans and SkinsRestorer v15, including LiteBans' BIT(1) flag columns.
 */
@EnabledIfEnvironmentVariable(named = "LOBBY_TEST_DB_PORT", matches = "\\d+")
class DatabaseReadersIT {

    private static final String PREFIX = "lobbytest_";
    private static final long HOUR = 3_600_000L;
    private static final UUID BANNED = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID EXPIRED = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OTHER_SERVER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID IP_BANNED_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID UNBANNED = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final UUID INNOCENT = UUID.fromString("00000000-0000-0000-0000-00000000000f");
    private static final String BANNED_IP = "203.0.113.7";

    private static DatabasePool pool;

    @BeforeAll
    static void createTables() throws Exception {
        pool = DatabasePool.connect(new IntegrationsConfig.Database(
                env("LOBBY_TEST_DB_HOST", "localhost"),
                Integer.parseInt(System.getenv("LOBBY_TEST_DB_PORT")),
                env("LOBBY_TEST_DB_NAME", "test"),
                env("LOBBY_TEST_DB_USER", "root"),
                env("LOBBY_TEST_DB_PASSWORD", ""),
                2));
        long now = System.currentTimeMillis();
        execute(
                "DROP TABLE IF EXISTS " + PREFIX + "bans, " + PREFIX + "mutes, " + PREFIX + "players, "
                        + PREFIX + "player_skins, " + PREFIX + "custom_skins, " + PREFIX + "url_skins",
                liteBansTable("bans"),
                liteBansTable("mutes"),
                // id, uuid, ip, reason, by, until, scope, ipban, active
                insert("bans", BANNED, "198.51.100.1", "Cheating", "Mod", 0, "*", 0, 1),
                insert("bans", EXPIRED, "198.51.100.2", "Old", "Mod", now - HOUR, "*", 0, 1),
                insert("bans", OTHER_SERVER, "198.51.100.3", "Bedwars only", "Mod", 0, "bedwars", 0, 1),
                insert("bans", IP_BANNED_ACCOUNT, BANNED_IP, "Alt accounts", "Admin", now + HOUR, "*", 1, 1),
                insert("bans", UNBANNED, "198.51.100.5", "Mistake", "Mod", 0, "*", 0, 0),
                insert("mutes", BANNED, "198.51.100.1", "Spam", "Mod", now + HOUR, "lobby", 0, 1),
                "CREATE TABLE " + PREFIX + "players (uuid VARCHAR(36) NOT NULL, skin_identifier VARCHAR(2083),"
                        + " skin_variant VARCHAR(20), skin_type VARCHAR(20), offline_mode_warning_dismissed BOOLEAN,"
                        + " PRIMARY KEY (uuid))",
                "CREATE TABLE " + PREFIX + "player_skins (uuid VARCHAR(36) NOT NULL, last_known_name VARCHAR(16),"
                        + " value TEXT NOT NULL, signature TEXT NOT NULL, timestamp BIGINT NOT NULL, PRIMARY KEY (uuid))",
                "CREATE TABLE " + PREFIX + "custom_skins (name VARCHAR(36) NOT NULL, display_name TEXT,"
                        + " value TEXT NOT NULL, signature TEXT NOT NULL, PRIMARY KEY (name))",
                "CREATE TABLE " + PREFIX + "url_skins (url VARCHAR(266) CHARACTER SET ascii NOT NULL,"
                        + " mine_skin_id VARCHAR(36), value TEXT NOT NULL, signature TEXT NOT NULL,"
                        + " skin_variant VARCHAR(20) NOT NULL, PRIMARY KEY (url, skin_variant))",
                "INSERT INTO " + PREFIX + "players VALUES ('" + BANNED + "', 'Notch-uuid', NULL, 'PLAYER', 0),"
                        + " ('" + EXPIRED + "', 'wizard', NULL, 'CUSTOM', 0),"
                        + " ('" + OTHER_SERVER + "', 'https://example.com/s.png', 'SLIM', 'URL', 0)",
                "INSERT INTO " + PREFIX + "player_skins VALUES ('Notch-uuid', 'Notch', 'v-premium', 's-premium', 0)",
                "INSERT INTO " + PREFIX + "custom_skins VALUES ('wizard', NULL, 'v-custom', 's-custom')",
                "INSERT INTO " + PREFIX + "url_skins VALUES ('https://example.com/s.png', NULL, 'v-url', 's-url', 'SLIM')");
    }

    @AfterAll
    static void dropTables() throws Exception {
        if (pool != null) {
            execute("DROP TABLE IF EXISTS " + PREFIX + "bans, " + PREFIX + "mutes, " + PREFIX + "players, "
                    + PREFIX + "player_skins, " + PREFIX + "custom_skins, " + PREFIX + "url_skins");
            pool.close();
        }
    }

    private final LiteBansQueries queries = new LiteBansQueries(PREFIX, "lobby");

    @Test
    void tablesAreFound() throws SQLException {
        pool.queryNow(connection -> {
            queries.checkTables(connection);
            return null;
        });
    }

    @Test
    void wrongPrefixGivesClearError() {
        LiteBansQueries wrong = new LiteBansQueries("nope_", "lobby");
        SQLException error = assertThrows(SQLException.class, () -> pool.queryNow(connection -> {
            wrong.checkTables(connection);
            return null;
        }));
        assertTrue(error.getMessage().contains("table-prefix"));
    }

    @Test
    void findsPermanentGlobalBan() throws SQLException {
        Optional<Punishment> ban = ban(BANNED, "192.0.2.1");
        assertTrue(ban.isPresent());
        assertEquals("Cheating", ban.get().reason());
        assertEquals("Mod", ban.get().punishedBy());
        assertTrue(ban.get().permanent());
    }

    @Test
    void ignoresExpiredRemovedAndOtherServerBans() throws SQLException {
        assertTrue(ban(EXPIRED, "192.0.2.1").isEmpty(), "expired");
        assertTrue(ban(UNBANNED, "192.0.2.1").isEmpty(), "removed (active = 0)");
        assertTrue(ban(OTHER_SERVER, "192.0.2.1").isEmpty(), "scoped to another server");
        assertTrue(ban(INNOCENT, "192.0.2.1").isEmpty(), "never banned");
    }

    @Test
    void ipBanCatchesOtherAccountsOnSameIp() throws SQLException {
        Optional<Punishment> ban = ban(INNOCENT, BANNED_IP);
        assertTrue(ban.isPresent());
        assertTrue(ban.get().ipBased());
        assertTrue(!ban.get().permanent());
    }

    @Test
    void serverScopedMuteForThisServerApplies() throws SQLException {
        Optional<Punishment> mute = pool.queryNow(c -> queries.activeMute(c, BANNED, "192.0.2.1", System.currentTimeMillis()));
        assertTrue(mute.isPresent());
        assertEquals("Spam", mute.get().reason());
    }

    @Test
    void pollingSeesNewMutesAndUnmutes() throws SQLException {
        long before = pool.queryNow(queries::latestMuteId);
        execute(insert("mutes", INNOCENT, "192.0.2.9", "Caps", "Helper", 0, "*", 0, 1));

        long now = System.currentTimeMillis();
        List<LiteBansQueries.MuteRow> rows = pool.queryNow(c -> queries.newMutes(c, before, now));
        assertEquals(1, rows.size());
        assertEquals(INNOCENT.toString(), rows.getFirst().uuid());
        long id = rows.getFirst().punishment().id();

        assertEquals(Set.of(id), pool.queryNow(c -> queries.stillActiveMutes(c, List.of(id), now)));
        execute("UPDATE " + PREFIX + "mutes SET active = 0 WHERE id = " + id);
        assertTrue(pool.queryNow(c -> queries.stillActiveMutes(c, List.of(id), now)).isEmpty());
    }

    @Test
    void skinsRestorerResolvesEverySkinType() throws SQLException {
        SkinsRestorerReader reader = new SkinsRestorerReader(pool, PREFIX);
        reader.checkTables();

        assertEquals(Optional.of(new SkinData("v-premium", "s-premium")), reader.chosenSkinNow(BANNED));
        assertEquals(Optional.of(new SkinData("v-custom", "s-custom")), reader.chosenSkinNow(EXPIRED));
        assertEquals(Optional.of(new SkinData("v-url", "s-url")), reader.chosenSkinNow(OTHER_SERVER));
        assertEquals(Optional.empty(), reader.chosenSkinNow(INNOCENT));
        assertEquals(Optional.of(new SkinData("v-custom", "s-custom")), reader.customSkin("wizard").join());
    }

    private Optional<Punishment> ban(UUID player, String ip) throws SQLException {
        return pool.queryNow(c -> queries.activeBan(c, player, ip, System.currentTimeMillis()));
    }

    private static String liteBansTable(String name) {
        return "CREATE TABLE " + PREFIX + name + " (id BIGINT NOT NULL AUTO_INCREMENT, uuid VARCHAR(36), ip VARCHAR(45),"
                + " reason VARCHAR(2048), banned_by_uuid VARCHAR(36), banned_by_name VARCHAR(128),"
                + " removed_by_uuid VARCHAR(36), removed_by_name VARCHAR(128), removed_by_reason VARCHAR(2048),"
                + " removed_by_date TIMESTAMP NULL, time BIGINT NOT NULL, until BIGINT NOT NULL, template TINYINT,"
                + " server_scope VARCHAR(32), server_origin VARCHAR(32), silent BIT(1) NOT NULL DEFAULT 0,"
                + " ipban BIT(1) NOT NULL, ipban_wildcard BIT(1) NOT NULL DEFAULT 0, active BIT(1) NOT NULL,"
                + " PRIMARY KEY (id))";
    }

    private static String insert(String table, UUID uuid, String ip, String reason, String by, long until,
                                 String scope, int ipban, int active) {
        return "INSERT INTO " + PREFIX + table + " (uuid, ip, reason, banned_by_name, time, until, server_scope, ipban, active)"
                + " VALUES ('" + uuid + "', '" + ip + "', '" + reason + "', '" + by + "', " + System.currentTimeMillis()
                + ", " + until + ", '" + scope + "', " + ipban + ", " + active + ")";
    }

    private static void execute(String... statements) throws SQLException {
        pool.queryNow(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return null;
        });
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
