package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Addon settings against a real MariaDB. Skipped unless {@code LOBBY_TEST_DB_PORT} is set; the same optional
 * variables as {@code DatabaseReadersIT}. The table uses the prefix {@code lobbytest_} and is dropped.
 */
@EnabledIfEnvironmentVariable(named = "LOBBY_TEST_DB_PORT", matches = "\\d+")
class AddonSettingsIT {

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
        execute("DROP TABLE IF EXISTS " + PREFIX + "addon_settings");
    }

    @AfterAll
    static void dropTable() throws Exception {
        execute("DROP TABLE IF EXISTS " + PREFIX + "addon_settings");
        pool.close();
    }

    @Test
    void keepsValuesPerAddonAndPlayer() throws Exception {
        AddonSettingsContract.check(DatabaseAddonSettings.create(pool, PREFIX));
    }

    @Test
    void creatingTheStoreAgainKeepsTheValues() throws Exception {
        UUID player = UUID.randomUUID();
        DatabaseAddonSettings.create(pool, PREFIX).save(player, "coins", "balance", "7");
        assertEquals(Optional.of("7"), DatabaseAddonSettings.create(pool, PREFIX).load(player, "coins", "balance"));
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
