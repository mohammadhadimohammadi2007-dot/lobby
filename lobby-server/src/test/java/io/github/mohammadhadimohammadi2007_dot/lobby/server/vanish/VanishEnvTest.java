package io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage.FileSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp.Kit;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanish in the whole lobby: who sees a vanished player, the counts and the tab list, PvP, and rejoining. */
@EnvTest
class VanishEnvTest {

    /** Operators (data/ops.yml), so they may vanish and see vanished players. */
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");

    @TempDir
    Path dir;

    private final List<LobbyServer> started = new ArrayList<>();
    private ConfigManager config;

    @AfterEach
    void stop() {
        started.forEach(lobby -> {
            lobby.chat().settings().flush();
            LobbyServerTestAccess.stop(lobby);
        });
        Vanished.clear();
    }

    private LobbyServer start() throws Exception {
        new ConfigManager(dir).load();
        Path main = dir.resolve(ConfigManager.CONFIG_FILE);
        Files.writeString(main, Files.readString(main).replace("protection: code-and-ip", "protection: off"));
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("data").resolve("ops.yml"), STAFF + ":\n  name: Staff\n  confirmed: true\n"
                + MOD + ":\n  name: Mod\n  confirmed: true\n");
        config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = LobbyServerTestAccess.start(config);
        started.add(lobby);
        return lobby;
    }

    private record Joined(Player player, TestConnection connection) {
    }

    private Joined join(Env env, LobbyServer lobby, UUID id, String name) {
        TestConnection connection = env.createConnection(new GameProfile(id, name));
        Player player = connection.connect(lobby.world().instance(), SpawnListener.spawnPosition(config.current().config()));
        lobby.chat().settings().whenLoaded(id).join();
        return new Joined(player, connection);
    }

    @Test
    void aVanishedPlayerIsGoneForPlayersAndCountsButNotForStaff(Env env) throws Exception {
        LobbyServer lobby = start();
        Player staff = join(env, lobby, STAFF, "Staff").player();
        Player mod = join(env, lobby, MOD, "Mod").player();
        Joined steve = join(env, lobby, UUID.randomUUID(), "Steve");
        env.tick();
        assertTrue(staff.getViewers().contains(steve.player()));
        assertTrue(lobby.vanish().canSeeVanished(mod), "operators may see vanished players");

        Collector<PlayerInfoUpdatePacket> tab = steve.connection().trackIncoming(PlayerInfoUpdatePacket.class);
        assertEquals(VanishService.Change.DONE, lobby.vanish().set(staff, true));
        env.tick();

        assertFalse(staff.getViewers().contains(steve.player()), "a player no longer sees them");
        assertTrue(staff.getViewers().contains(mod), "staff with lobby.vanish.see still do");
        assertTrue(tab.collect().stream().anyMatch(packet -> packet.actions().contains(PlayerInfoUpdatePacket.Action.UPDATE_LISTED)
                && packet.entries().stream().anyMatch(entry -> entry.uuid().equals(STAFF) && !entry.listed())),
                "taken out of the player's tab list");
        // The count is cached for a moment, and the scoreboard may have read it just before.
        env.tickWhile(() -> !"2".equals(lobby.text().placeholders().plainText("%server_online%", null)),
                java.time.Duration.ofSeconds(5));
        assertEquals("2", lobby.text().placeholders().plainText("%server_online%", null), "3 online, 1 vanished");
        assertEquals("1", lobby.text().placeholders().plainText("%vanish_count%", null));
        assertTrue(lobby.chat().settings().get(STAFF).vanished(), "kept for the next join");

        assertEquals(VanishService.Change.UNCHANGED, lobby.vanish().set(staff, true));
        assertEquals(VanishService.Change.DONE, lobby.vanish().set(staff, false));
        env.tick();
        assertTrue(staff.getViewers().contains(steve.player()), "visible again");
        assertFalse(lobby.chat().settings().get(STAFF).vanished());
    }

    @Test
    void vanishedStaffHaveNoCosmeticsAndStayOutOfThePvpArea(Env env) throws Exception {
        LobbyServer lobby = start();
        lobby.pvp().setRegion(new Region("wand", "selection", -10, 30, -10, 10, 90, 10));
        lobby.pvp().setKit(new Kit(Map.of(0, ItemStack.of(Material.IRON_SWORD)), Map.of()));
        Player mod = join(env, lobby, MOD, "Mod").player();
        env.tick();
        env.tick();
        assertTrue(lobby.pvp().inArea(mod));
        assertEquals(VanishService.Change.IN_PVP_AREA, lobby.vanish().set(mod, true), "leave the area first");

        // Vanished from an earlier visit: they walk through the area without the kit.
        Vanished.set(STAFF, true);
        Player staff = join(env, lobby, STAFF, "Staff").player();
        env.tick();
        env.tick();
        assertFalse(lobby.pvp().inArea(staff));
        assertTrue(lobby.cosmetics().suppressed(staff), "no trails or gadgets that would give them away");
    }

    @Test
    void aVanishedPlayerWhoRejoinsIsVanishedBeforeAnyoneSeesThem(Env env) throws Exception {
        LobbyServer lobby = start();
        Joined staff = join(env, lobby, STAFF, "Staff");
        lobby.vanish().set(staff.player(), true);
        lobby.chat().settings().flush();
        assertTrue(new FileSettingsStore(dir).load(STAFF).orElseThrow().vanished(), "saved");

        staff.player().remove();
        env.tick();
        env.tick();
        assertFalse(Vanished.is(STAFF), "forgotten after leaving");

        Joined steve = join(env, lobby, UUID.randomUUID(), "Steve");
        Player again = join(env, lobby, STAFF, "Staff").player();
        assertTrue(Vanished.is(again), "read again during login");
        env.tick();
        assertFalse(again.getViewers().contains(steve.player()));
    }
}
