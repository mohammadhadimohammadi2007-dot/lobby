package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What every addon settings store must do; run against the files and the database. */
final class AddonSettingsContract {

    private AddonSettingsContract() {
    }

    static void check(AddonSettingsStore store) throws Exception {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(Optional.empty(), store.load(player, "coins", "balance"));

        store.save(player, "coins", "balance", "10");
        store.save(player, "coins", "streak", "3");
        store.save(player, "stats", "balance", "other addon");
        assertEquals(Optional.of("10"), store.load(player, "coins", "balance"));
        assertEquals(Optional.of("3"), store.load(player, "coins", "streak"));
        assertEquals(Optional.of("other addon"), store.load(player, "stats", "balance"), "each addon has its own values");
        assertEquals(Optional.empty(), store.load(other, "coins", "balance"), "and each player");

        store.save(player, "coins", "balance", "سلام 25");
        assertEquals(Optional.of("سلام 25"), store.load(player, "coins", "balance"), "overwritten, any text");

        store.save(player, "coins", "balance", null);
        assertEquals(Optional.empty(), store.load(player, "coins", "balance"), "null removes it");
        assertEquals(Optional.of("3"), store.load(player, "coins", "streak"), "the others stay");
        store.save(player, "coins", "never-set", null);
    }
}
