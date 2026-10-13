package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.LobbyServerTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeTestAccess;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PvP area in the whole lobby: kit, hits by version, kills, combat tag. */
@EnvTest
class PvpEnvTest {

    private static final double DELTA = 1.0E-4;

    @TempDir
    Path dir;

    private final List<LobbyServer> started = new ArrayList<>();
    private ConfigManager config;

    @AfterEach
    void stop() {
        started.forEach(LobbyServerTestAccess::stop);
    }

    /** The whole lobby with a PvP area around the spawn and a kit of a diamond sword and an iron chestplate. */
    private LobbyServer start(String pvpYml) throws Exception {
        if (pvpYml != null) {
            Files.writeString(dir.resolve(ConfigManager.PVP_FILE), pvpYml);
        }
        config = new ConfigManager(dir);
        config.load();
        LobbyServer lobby = LobbyServerTestAccess.start(config);
        started.add(lobby);
        PvpService pvp = lobby.pvp();
        pvp.setRegion(new Region("wand", "selection", -10, 60, -10, 10, 75, 10));
        pvp.setKit(new Kit(Map.of(0, ItemStack.of(Material.DIAMOND_SWORD)),
                Map.of(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.IRON_CHESTPLATE))));
        return lobby;
    }

    private Player join(Env env, LobbyServer lobby, String name, int protocol) {
        UUID id = UUID.randomUUID();
        if (protocol != 0) {
            BridgeTestAccess.reportVersion(lobby.bridge(), id, protocol);
        }
        return env.createConnection(new GameProfile(id, name))
                .connect(lobby.world().instance(), SpawnListener.spawnPosition(config.current().config()));
    }

    private static void attack(Player attacker, Player victim) {
        EventDispatcher.call(new EntityAttackEvent(attacker, victim));
    }

    @Test
    void walkingInGivesTheKitAndLeavingGivesTheLobbyItemsBack(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player steve = join(env, lobby, "Steve", 0);
        env.tick();
        env.tick();

        assertTrue(lobby.pvp().inArea(steve), "the spawn is inside the area");
        assertEquals(Material.DIAMOND_SWORD, steve.getInventory().getItemStack(0).material());
        assertEquals(Material.IRON_CHESTPLATE, steve.getEquipment(EquipmentSlot.CHESTPLATE).material());
        assertFalse(steve.isAllowFlying(), "no double jump in the area");

        lobby.pvp().leave(steve);
        env.tickWhile(() -> steve.getInventory().getItemStack(0).getTag(HotbarService.ITEM_TAG) == null,
                Duration.ofSeconds(5));
        assertEquals(Material.AIR, steve.getEquipment(EquipmentSlot.CHESTPLATE).material());
        assertFalse(lobby.pvp().inArea(steve));
    }

    @Test
    void eachHitFollowsTheAttackersVersion(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player old = join(env, lobby, "Old", ProtocolVersions.V1_8);
        Player modern = join(env, lobby, "Modern", 0);
        old.getInventory().clear();
        old.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
        modern.getInventory().clear();
        modern.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));

        // 1.8 rules: a diamond sword deals 8, whatever the modern player wears.
        attack(old, modern);
        assertEquals(20 - 8, modern.getHealth(), DELTA);
        // Modern rules, first swing fully charged: 7.
        attack(modern, old);
        assertEquals(20 - 7, old.getHealth(), DELTA);

        assertTrue(lobby.pvp().inCombat(old));
        assertTrue(lobby.pvp().inCombat(modern));
        // Right away again: the 1.8 player has no cooldown, but the hurt timer only lets a stronger hit through.
        attack(old, modern);
        assertEquals(20 - 8, modern.getHealth(), DELTA);
    }

    @Test
    void sameVersionOnlyKeepsVersionsApart(Env env) throws Exception {
        LobbyServer lobby = start("same-version-only: true\n");
        Player old = join(env, lobby, "Old", ProtocolVersions.V1_8);
        Player modern = join(env, lobby, "Modern", 0);
        Player other = join(env, lobby, "Other", 0);

        attack(old, modern);
        assertEquals(20, modern.getHealth(), DELTA);
        assertFalse(lobby.pvp().inCombat(old));

        attack(other, modern);
        assertTrue(modern.getHealth() < 20, "modern against modern still works");
    }

    @Test
    void theLastHitCountsAKillAndSendsTheVictimBack(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player killer = join(env, lobby, "Killer", 0);
        Player victim = join(env, lobby, "Victim", 0);
        env.tick();
        lobby.pvp().setSpawn(new Pos(5.5, 65, 5.5));
        victim.setHealth(2);

        attack(killer, victim);

        assertEquals(20, victim.getHealth(), DELTA, "healed instead of dying");
        assertFalse(victim.isDead());
        assertFalse(lobby.pvp().inCombat(victim), "combat ends with the death");
        assertEquals(Material.DIAMOND_SWORD, victim.getInventory().getItemStack(0).material(), "a fresh kit");
        PvpStats killerStats = lobby.pvp().stats().get(killer.getUuid(), "Killer");
        PvpStats victimStats = lobby.pvp().stats().get(victim.getUuid(), "Victim");
        assertEquals(1, killerStats.kills());
        assertEquals(1, killerStats.streak());
        assertEquals(1, victimStats.deaths());
        assertEquals("1", lobby.text().placeholders().plainText("%pvp_kills%", killer));
        assertEquals("1.00", lobby.text().placeholders().plainText("%pvp_kdr%", killer));
    }

    @Test
    void inCombatNobodyLeavesSwitchesOrUsesBlockedCommands(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player steve = join(env, lobby, "Steve", 0);
        Player alex = join(env, lobby, "Alex", 0);
        env.tick();
        attack(steve, alex);
        assertTrue(lobby.pvp().inCombat(alex));

        PlayerMoveEvent out = new PlayerMoveEvent(alex, new Pos(30, 65, 0), true);
        EventDispatcher.call(out);
        assertTrue(out.isCancelled(), "walking out is undone");

        assertFalse(lobby.actions().connector().connect(alex, "bedwars").join());
        // /spawn is on combat-blocked-commands, /fly on area-blocked-commands.
        assertFalse(MinecraftServer.getCommandManager().execute(alex, "spawn").getType()
                == net.minestom.server.command.builder.CommandResult.Type.SUCCESS);
        assertTrue(lobby.pvp().inArea(alex));

        PlayerMoveEvent free = new PlayerMoveEvent(steve, new Pos(0.5, 65, 1.5), true);
        EventDispatcher.call(free);
        assertFalse(free.isCancelled(), "moving inside the area is fine");
    }

    @Test
    void creativePlayersAndPlayersOutsideCannotFight(Env env) throws Exception {
        LobbyServer lobby = start(null);
        Player steve = join(env, lobby, "Steve", 0);
        Player alex = join(env, lobby, "Alex", 0);
        env.tick();
        steve.setGameMode(net.minestom.server.entity.GameMode.CREATIVE);
        attack(steve, alex);
        assertEquals(20, alex.getHealth(), DELTA);

        steve.setGameMode(net.minestom.server.entity.GameMode.ADVENTURE);
        lobby.pvp().leave(alex);
        attack(steve, alex);
        assertEquals(20, alex.getHealth(), DELTA, "outside the area nobody gets hurt");
    }
}
