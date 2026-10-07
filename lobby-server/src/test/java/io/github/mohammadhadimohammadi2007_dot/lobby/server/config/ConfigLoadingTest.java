package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.connection.ConnectionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoadingTest {

    @TempDir
    Path dir;

    @Test
    void firstStartCreatesFilesAndBootsWithSafeDefaults() throws Exception {
        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertTrue(Files.exists(dir.resolve("config.yml")));
        assertTrue(Files.exists(dir.resolve("integrations.yml")));
        assertTrue(Files.exists(dir.resolve("messages.yml")));
        assertEquals(List.of(), snapshot.warnings(), "default files must load without warnings");

        LobbyConfig config = snapshot.config();
        assertEquals(ConnectionMode.STANDALONE, config.connection().mode());
        assertFalse(config.connection().onlineMode());
        assertEquals(25565, config.server().port());
        assertEquals(200, config.server().maxPlayers());
        assertFalse(snapshot.integrations().needsDatabase());
        assertFalse(snapshot.integrations().bridge().enabled());
    }

    @Test
    void existingFileIsNeverOverwritten() throws Exception {
        String custom = "server:\n  port: 25570\n";
        Files.writeString(dir.resolve("config.yml"), custom);

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertEquals(custom, Files.readString(dir.resolve("config.yml")));
        assertEquals(25570, snapshot.config().server().port());
    }

    @Test
    void missingOptionUsesDefaultAndWarns() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "server:\n  port: 25570\n");

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertEquals(200, snapshot.config().server().maxPlayers());
        assertTrue(hasWarning(snapshot, "'server.max-players' is missing or empty"), snapshot.warnings().toString());
    }

    @Test
    void invalidValueNamesOptionAndAllowedValues() throws Exception {
        writeConfigReplacing("port: 25565", "port: banana");

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertEquals(25565, snapshot.config().server().port());
        assertTrue(hasWarning(snapshot, "'server.port' has an invalid value 'banana'. Allowed: a whole number from 1 to 65535"),
                snapshot.warnings().toString());
    }

    @Test
    void outOfRangeValueIsRejected() throws Exception {
        writeConfigReplacing("preload-radius: 6", "preload-radius: 500");

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertEquals(6, snapshot.config().world().preloadRadius());
        assertTrue(hasWarning(snapshot, "'world.preload-radius'"));
    }

    @Test
    void booleansAcceptYesNoAndRejectOtherWords() throws Exception {
        writeConfigReplacing("block-break: false", "block-break: \"yes\"");
        writeConfigReplacing("hunger: false", "hunger: maybe");

        ConfigSnapshot snapshot = new ConfigManager(dir).load();

        assertTrue(snapshot.config().protection().blockBreak());
        assertFalse(snapshot.config().protection().hunger());
        assertTrue(hasWarning(snapshot, "'protection.hunger' has an invalid value 'maybe'. Allowed: true or false"));
    }

    @Test
    void modeIsCaseInsensitive() throws Exception {
        writeConfigReplacing("mode: standalone", "mode: BungeeCord");

        assertEquals(ConnectionMode.BUNGEECORD, new ConfigManager(dir).load().config().connection().mode());
    }

    @Test
    void unknownModeStopsTheServer() throws Exception {
        writeConfigReplacing("mode: standalone", "mode: velocty");

        ConfigException error = assertThrows(ConfigException.class, () -> new ConfigManager(dir).load());
        assertTrue(error.getMessage().contains("'mode'"));
        assertTrue(error.getMessage().contains("bungeecord, standalone, velocity"));
    }

    @Test
    void velocityModeNeedsSecret() throws Exception {
        writeConfigReplacing("mode: standalone", "mode: velocity");

        ConfigException error = assertThrows(ConfigException.class, () -> new ConfigManager(dir).load());
        assertTrue(error.getMessage().contains("velocity-secret"));
    }

    @Test
    void velocityModeWithSecretLoads() throws Exception {
        writeConfigReplacing("mode: standalone", "mode: velocity");
        writeConfigReplacing("velocity-secret: \"\"", "velocity-secret: \"abc123\"");

        LobbyConfig config = new ConfigManager(dir).load().config();
        assertEquals("abc123", config.connection().velocitySecret());
        assertFalse(config.connection().toString().contains("abc123"), "secret must not be printed");
    }

    @Test
    void operatorsAreLowerCased() throws Exception {
        writeConfigReplacing("operators: []", "operators: [\"Notch\", \"jeb_\", \"\"]");

        assertEquals(java.util.Set.of("notch", "jeb_"), new ConfigManager(dir).load().config().operators());
    }

    @Test
    void emptyOptionUsesDefaultAndWarns() throws Exception {
        writeConfigReplacing("operators: []", "operators:");

        ConfigSnapshot snapshot = new ConfigManager(dir).load();
        assertTrue(snapshot.config().operators().isEmpty());
        assertTrue(hasWarning(snapshot, "'operators' is missing or empty"));
    }

    @Test
    void brokenYamlGivesClearError() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "server:\n  port: 1\n\tmotd: [broken\n");

        ConfigException error = assertThrows(ConfigException.class, () -> new ConfigManager(dir).load());
        assertTrue(error.getMessage().startsWith("config.yml is not valid YAML"), error.getMessage());
    }

    @Test
    void unsafeTablePrefixIsRejected() throws Exception {
        new ConfigManager(dir).load();
        replace("integrations.yml", "table-prefix: \"litebans_\"", "table-prefix: \"x; DROP TABLE y\"");

        assertThrows(ConfigException.class, () -> new ConfigManager(dir).load());
    }

    @Test
    void reloadReportsOnlyRestartOptions() throws Exception {
        ConfigManager manager = new ConfigManager(dir);
        manager.load();
        replace("config.yml", "port: 25565", "port: 25570");
        replace("config.yml", "max-players: 200", "max-players: 100");

        ConfigManager.ReloadResult result = manager.reload();

        assertEquals(List.of("server.port"), result.restartNeeded());
        assertEquals(100, manager.current().config().server().maxPlayers());
    }

    @Test
    void failedReloadKeepsOldConfig() throws Exception {
        ConfigManager manager = new ConfigManager(dir);
        manager.load();
        replace("config.yml", "mode: standalone", "mode: nope");

        assertThrows(ConfigException.class, manager::reload);
        assertEquals(ConnectionMode.STANDALONE, manager.current().config().connection().mode());
    }

    @Test
    void messagesRenderWithPrefixAndPlainTextPlaceholders() throws Exception {
        Messages messages = new ConfigManager(dir).load().messages();

        var component = messages.render(MessageKey.SETSPAWN_FAILED, Messages.text("error", "<red>boom"));
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
        assertEquals("[Lobby] Could not save the spawn: <red>boom", plain);
    }

    private void writeConfigReplacing(String from, String to) throws IOException {
        if (!Files.exists(dir.resolve("config.yml"))) {
            ConfigFiles.createIfMissing(dir, "config.yml");
        }
        replace("config.yml", from, to);
    }

    private void replace(String file, String from, String to) throws IOException {
        Path path = dir.resolve(file);
        String text = Files.readString(path);
        assertTrue(text.contains(from), "test setup: '" + from + "' not found in " + file);
        Files.writeString(path, text.replace(from, to));
    }

    private static boolean hasWarning(ConfigSnapshot snapshot, String text) {
        return snapshot.warnings().stream().anyMatch(warning -> warning.contains(text));
    }
}
