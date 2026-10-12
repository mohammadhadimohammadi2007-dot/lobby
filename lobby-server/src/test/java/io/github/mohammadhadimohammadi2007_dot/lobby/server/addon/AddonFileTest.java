package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonFileTest {

    @Test
    void readsEveryField() throws Exception {
        AddonDescription description = AddonFile.parse("""
                {"id": "Parkour-Tools", "name": "Parkour Tools", "version": "1.2.0", "main": "com.example.Main",
                 "api-version": "0.1", "authors": ["Ann", "Bob"], "depends": ["Economy"], "soft-depends": ["stats"]}
                """);
        assertEquals("parkour-tools", description.id());
        assertEquals("Parkour Tools", description.name());
        assertEquals("1.2.0", description.version());
        assertEquals("com.example.Main", description.mainClass());
        assertEquals("0.1", description.apiVersion());
        assertEquals(List.of("Ann", "Bob"), description.authors());
        assertEquals(List.of("economy"), description.depends());
        assertEquals(List.of("stats"), description.softDepends());
    }

    @Test
    void theNameDefaultsToTheIdAndListsAreOptional() throws Exception {
        AddonDescription description = AddonFile.parse(
                "{\"id\": \"a\", \"version\": \"1\", \"main\": \"x.A\", \"api-version\": \"0.1\"}");
        assertEquals("a", description.name());
        assertTrue(description.depends().isEmpty());
    }

    @Test
    void mistakesAreExplained() {
        assertEquals("'version' is missing", assertThrows(AddonFile.DescriptionException.class, () -> AddonFile.parse(
                "{\"id\": \"a\", \"main\": \"x.A\", \"api-version\": \"0.1\"}")).getMessage());
        assertTrue(assertThrows(AddonFile.DescriptionException.class, () -> AddonFile.parse(
                "{\"id\": \"has space\", \"version\": \"1\", \"main\": \"x.A\", \"api-version\": \"0.1\"}"))
                .getMessage().contains("lowercase letters"));
        assertTrue(assertThrows(AddonFile.DescriptionException.class, () -> AddonFile.parse("{not json"))
                .getMessage().contains("not valid JSON"));
        assertTrue(assertThrows(AddonFile.DescriptionException.class, () -> AddonFile.parse(
                "{\"id\": \"a\", \"version\": \"1\", \"main\": \"x.A\", \"api-version\": \"0.1\", \"depends\": \"b\"}"))
                .getMessage().contains("must be a list"));
    }

    @Test
    void addonsGetOnlyTheApiAndMinestomFromTheServer() {
        assertTrue(AddonClassLoader.shared("io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon"));
        assertTrue(AddonClassLoader.shared("net.minestom.server.entity.Player"));
        assertTrue(AddonClassLoader.shared("net.kyori.adventure.text.Component"));
        assertFalse(AddonClassLoader.shared("io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer"));
        assertFalse(AddonClassLoader.shared("io.github.mohammadhadimohammadi2007_dot.lobby.common.bridge.BridgeMessage"));
        assertFalse(AddonClassLoader.shared("org.spongepowered.configurate.ConfigurationNode"));
        assertFalse(AddonClassLoader.shared("com.zaxxer.hikari.HikariDataSource"));
    }
}
