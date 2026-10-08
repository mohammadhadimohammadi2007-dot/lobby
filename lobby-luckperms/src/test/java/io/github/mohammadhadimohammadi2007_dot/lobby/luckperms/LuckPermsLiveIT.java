package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LuckPerms against a real MariaDB: permission checks, prefix and group, and a rank change made by a
 * separate LuckPerms instance (another JVM) reaching the lobby through SQL messaging.
 *
 * <p>Skipped unless {@code LOBBY_TEST_DB_PORT} is set (see CONTRIBUTING.md). Uses its own database
 * {@code lobby_luckperms_it}, dropped and recreated on every run.
 */
@EnvTest
@EnabledIfEnvironmentVariable(named = "LOBBY_TEST_DB_PORT", matches = "\\d+")
class LuckPermsLiveIT {

    private static final String DATABASE = "lobby_luckperms_it";
    private static final String TABLE_PREFIX = "luckperms_";
    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PLAYER_NAME = "Tester";
    /** How long a change may take to arrive. SQL messaging polls about once a second. */
    private static final long ARRIVAL_TIMEOUT_SECONDS = 20;

    @TempDir
    Path dataDir;

    @Test
    void rankChangeFromAnotherInstanceArrivesLive(Env env) throws Exception {
        IntegrationsConfig.Database database = recreateDatabase();
        IntegrationsConfig.LuckPerms settings = new IntegrationsConfig.LuckPerms(true, "lobby", TABLE_PREFIX, "sql");

        LuckPermsPermissionService service = (LuckPermsPermissionService)
                new LobbyLuckPermsIntegration().start(dataDir, database, settings);
        try {
            Instance instance = env.createFlatInstance();
            Player player = env.createConnection(new GameProfile(PLAYER_ID, PLAYER_NAME)).connect(instance, new Pos(0, 42, 0));
            // The real login listener loads the user; the test harness skips the login phase.
            service.api().getUserManager().loadUser(PLAYER_ID, PLAYER_NAME).join();

            assertFalse(service.hasPermission(player, SecondLuckPermsInstance.PERMISSION), "no rank yet");
            assertEquals("", service.meta(player).prefix());
            assertEquals("default", service.meta(player).primaryGroup());

            List<UUID> changes = new CopyOnWriteArrayList<>();
            service.onMetaChange(changes::add);

            runSecondInstance(database);
            // Measured from the moment the other instance has saved the change and exited.
            long saved = System.nanoTime();
            waitUntil(() -> service.hasPermission(player, SecondLuckPermsInstance.PERMISSION));
            long arrivalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - saved);
            System.out.println("Rank change arrived " + arrivalMillis + " ms after the other instance saved it");

            PlayerMeta meta = service.meta(player);
            assertEquals(SecondLuckPermsInstance.PREFIX, meta.prefix());
            assertEquals(SecondLuckPermsInstance.GROUP, meta.primaryGroup());
            assertTrue(changes.contains(PLAYER_ID), "UserDataRecalculateEvent must reach onMetaChange listeners");
            assertFalse(service.hasPermission(player, Permissions.BYPASS_PROTECTION), "only the granted permission");
        } finally {
            LuckPermsMinestom.disable();
        }
    }

    private static void runSecondInstance(IntegrationsConfig.Database database) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"),
                SecondLuckPermsInstance.class.getName(),
                database.host(), String.valueOf(database.port()), database.database(),
                database.username(), database.password(), TABLE_PREFIX, PLAYER_ID.toString(), PLAYER_NAME));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> output.append(line).append('\n'));
        }
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "second instance did not finish");
        assertTrue(output.toString().contains(SecondLuckPermsInstance.DONE), "second instance failed:\n" + output);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ARRIVAL_TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "rank change did not arrive within " + ARRIVAL_TIMEOUT_SECONDS + "s");
            Thread.sleep(100);
        }
    }

    private static IntegrationsConfig.Database recreateDatabase() throws Exception {
        String host = env("LOBBY_TEST_DB_HOST", "localhost");
        int port = Integer.parseInt(System.getenv("LOBBY_TEST_DB_PORT"));
        String user = env("LOBBY_TEST_DB_USER", "root");
        String password = env("LOBBY_TEST_DB_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection("jdbc:mariadb://" + host + ":" + port + "/", user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        return new IntegrationsConfig.Database(host, port, DATABASE, user, password, 2);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
