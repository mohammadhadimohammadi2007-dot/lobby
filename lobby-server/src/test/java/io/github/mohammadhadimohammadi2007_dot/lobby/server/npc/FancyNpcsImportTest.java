package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Hologram;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importing a FancyNpcs npcs.yml, in the exact shape FancyNpcs' NpcManagerImpl writes it. */
@EnvTest
class FancyNpcsImportTest {

    @TempDir
    Path dir;

    private final List<NpcService> started = new ArrayList<>();
    private final List<ClientObjectRenderer> renderers = new ArrayList<>();

    @AfterEach
    void stop() {
        started.forEach(NpcService::shutdown);
        renderers.forEach(ClientObjectRenderer::shutdown);
    }

    private NpcService service(Env env) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        ClientObjectRenderer renderer = new ClientObjectRenderer();
        renderers.add(renderer);
        NpcService npcs = NpcService.start(dir, renderer,
                new Hologram.Services(text, permissions, player -> false, WorldScope.anywhere()),
                new ActionServices(config, text, permissions, bridge), new NpcSkins(null, null),
                new TeamManager(player -> 0, false));
        started.add(npcs);
        return npcs;
    }

    private void writeImport(String yaml) throws Exception {
        Path file = dir.resolve("import").resolve("npcs.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }

    @Test
    void aFancyNpcsFileIsConverted(Env env) throws Exception {
        writeImport("""
                npcs:
                  3f2a9c1e-0000-4000-8000-000000000001:
                    name: BedWars
                    creator: 00000000-0000-0000-0000-000000000000
                    displayName: "<gold>BedWars"
                    type: PLAYER
                    location:
                      world: world
                      x: 10.5
                      y: 65.0
                      z: -3.5
                      yaw: 180.0
                      pitch: 0.0
                    showInTab: true
                    spawnEntity: true
                    collidable: true
                    glowing: true
                    glowingColor: dark_purple
                    turnToPlayer: true
                    turnToPlayerDistance: 7
                    interactionCooldown: 1.5
                    scale: 1.5
                    attributes:
                      pose: crouching
                      on_fire: 'true'
                    visibility_distance: 30
                    skin:
                      value: dGV4dHVyZQ==
                      signature: c2lnbmF0dXJl
                      mirrorSkin: false
                    equipment:
                      MAINHAND:
                        ==: org.bukkit.inventory.ItemStack
                        v: 3700
                        type: RED_BED
                      HEAD:
                        ==: org.bukkit.inventory.ItemStack
                        DataVersion: 4189
                        id: minecraft:golden_helmet
                        count: 1
                      OFFHAND:
                        ==: org.bukkit.inventory.ItemStack
                        v: 3700
                        type: SHIELD
                      CHEST:
                        ==: org.bukkit.inventory.ItemStack
                        v: 3700
                        type: LEATHER_CHESTPLATE
                        meta:
                          ==: ItemMeta
                          meta-type: COLORABLE_ARMOR
                          color: {==: Color, RED: 255, BLUE: 0, GREEN: 0}
                      LEGS:
                        ==: org.bukkit.inventory.ItemStack
                        v: 3700
                        type: IRON_LEGGINGS
                      FEET:
                        ==: org.bukkit.inventory.ItemStack
                        v: 3700
                        type: DIAMOND_BOOTS
                    actions:
                      ANY_CLICK:
                        1:
                          action: message
                          value: "<green>Good luck!"
                        2:
                          action: send_to_server
                          value: bedwars-1
                      RIGHT_CLICK:
                        1:
                          action: wait
                          value: "2"
                        2:
                          action: player_command_as_op
                          value: gamemode creative
                  3f2a9c1e-0000-4000-8000-000000000002:
                    name: Trader
                    displayName: <empty>
                    type: VILLAGER
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0, yaw: 0.0, pitch: 0.0}
                    skin:
                      mirrorSkin: false
                """);
        NpcService npcs = service(env);

        FancyNpcsImport.Result result = FancyNpcsImport.run(dir, npcs);

        assertEquals(2, result.imported().size());
        NpcData bedwars = npcs.get("bedwars");
        assertNotNull(bedwars);
        assertEquals(new Pos(10.5, 65, -3.5, 180f, 0f), bedwars.position());
        assertEquals(EntityType.PLAYER, bedwars.type());
        assertEquals(List.of("<gold>BedWars"), bedwars.nameTag().lines());
        assertTrue(bedwars.glowing());
        assertTrue(bedwars.turnToPlayer());
        assertEquals(7, bedwars.turnDistance());
        assertEquals(30, bedwars.viewDistance());
        // FancyNpcs keeps the cooldown in seconds.
        assertEquals(1500, bedwars.clickCooldownMillis());
        assertEquals(new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl"), bedwars.resolvedSkin());
        assertEquals(Map.of(EquipmentSlot.MAIN_HAND, Material.RED_BED, EquipmentSlot.HELMET, Material.GOLDEN_HELMET,
                EquipmentSlot.OFF_HAND, Material.SHIELD, EquipmentSlot.CHESTPLATE, Material.LEATHER_CHESTPLATE,
                EquipmentSlot.LEGGINGS, Material.IRON_LEGGINGS, EquipmentSlot.BOOTS, Material.DIAMOND_BOOTS),
                bedwars.equipment(), "all six of FancyNpcs' slots, both ways Bukkit writes an item");
        assertTrue(result.skipped().stream().anyMatch(note -> note.contains("CHEST") && note.contains("colour")),
                "the dye of the chestplate is reported as lost: " + result.skipped());
        assertEquals(TeamColor.DARK_PURPLE, bedwars.glowColor());
        assertEquals(1.5, bedwars.scale());
        assertTrue(bedwars.showInTab());
        assertEquals(NpcPose.CROUCHING, bedwars.pose());
        assertTrue(result.skipped().stream().anyMatch(note -> note.contains("on_fire")),
                "an attribute this lobby has no setting for is reported: " + result.skipped());
        assertEquals(List.of("message: <green>Good luck!", "connect: bedwars-1"),
                bedwars.actions(NpcTrigger.ANY_CLICK).entries());
        // wait is in seconds there, and the operator command is refused.
        assertEquals(List.of("wait: 40"), bedwars.actions(NpcTrigger.RIGHT_CLICK).entries());
        assertTrue(result.skipped().stream().anyMatch(note -> note.contains("player_command_as_op")),
                "the refused action is reported: " + result.skipped());

        NpcData trader = npcs.get("trader");
        assertNotNull(trader);
        assertEquals(EntityType.VILLAGER, trader.type());
        assertEquals(List.of(), trader.nameTag().lines(), "<empty> means no name above it");
    }

    @Test
    void anExistingNpcIsNeverOverwritten(Env env) throws Exception {
        writeImport("""
                npcs:
                  some-id:
                    name: guide
                    displayName: "From FancyNpcs"
                    type: PLAYER
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                """);
        NpcService npcs = service(env);
        npcs.create("guide", new Pos(5, 65, 5));

        FancyNpcsImport.Result result = FancyNpcsImport.run(dir, npcs);

        assertEquals(List.of(), result.imported());
        assertEquals(1, result.skipped().size());
        assertEquals(new Pos(5, 65, 5), npcs.get("guide").position());
    }

    @Test
    void nothingToImportWhenTheFileIsMissing(Env env) throws Exception {
        FancyNpcsImport.Result result = FancyNpcsImport.run(dir, service(env));

        assertEquals(List.of(), result.imported());
        assertEquals(FancyNpcsImport.expectedFile(dir), result.file());
    }
}
