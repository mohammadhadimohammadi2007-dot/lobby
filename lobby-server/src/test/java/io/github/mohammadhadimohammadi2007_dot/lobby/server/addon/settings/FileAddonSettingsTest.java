package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FileAddonSettingsTest {

    @TempDir
    Path dir;

    @Test
    void keepsValuesPerAddonAndPlayer() throws Exception {
        AddonSettingsContract.check(new FileAddonSettings(dir));
    }

    @Test
    void aPlayerWithoutValuesHasNoFile() throws Exception {
        FileAddonSettings store = new FileAddonSettings(dir);
        UUID player = UUID.randomUUID();
        store.save(player, "coins", "balance", "1");
        store.save(player, "coins", "balance", null);
        assertFalse(Files.exists(dir.resolve("data/addon-settings/coins/" + player + ".json")));
    }
}
