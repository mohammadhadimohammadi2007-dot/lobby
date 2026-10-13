package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Statement;

/**
 * PvP records against a real MariaDB. Skipped unless {@code LOBBY_TEST_DB_PORT} is set; the same optional
 * variables as {@code DatabaseReadersIT}. The table uses the prefix {@code lobbytest_} and is dropped.
 */
@EnabledIfEnvironmentVariable(named = "LOBBY_TEST_DB_PORT", matches = "\\d+")
class PvpStatsIT {

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
        execute("DROP TABLE IF EXISTS " + PREFIX + "pvp_stats");
    }

    @AfterAll
    static void dropTable() throws Exception {
        execute("DROP TABLE IF EXISTS " + PREFIX + "pvp_stats");
        pool.close();
    }

    @Test
    void keepsRecordsAndLeaderboards() throws Exception {
        PvpStatsContract.check(DatabasePvpStats.create(pool, PREFIX));
        // Creating it again keeps the table.
        DatabasePvpStats.create(pool, PREFIX);
    }

    private static void execute(String sql) throws Exception {
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