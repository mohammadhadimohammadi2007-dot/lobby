package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.WorldScope;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Importing a FancyHolograms holograms.yml an admin copied into the import folder. */
class FancyHologramsImportTest {

    @TempDir
    Path dir;

    private final List<HologramService> started = new ArrayList<>();

    /** Saving runs in the background, so every service is closed before the temp folder is deleted. */
    @AfterEach
    void stopServices() {
        started.forEach(HologramService::shutdown);
    }

    private HologramService service() throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        PermissionService permissions = new OperatorPermissionService(config);
        BridgeService bridge = new BridgeService(false);
        LobbyText text = new LobbyText(config, new PlaceholderService(new PlaceholderRegistry()));
        HologramService service = HologramService.start(dir, new ClientObjectRenderer(),
                new Hologram.Services(text, permissions, player -> bridge.capabilities(player).legacy(), WorldScope.anywhere()),
                new ActionServices(config, text, permissions, bridge));
        started.add(service);
        return service;
    }

    private void writeImport(String yaml) throws Exception {
        Path file = dir.resolve("import").resolve("holograms.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml);
    }

    @Test
    void nothingToImportWhenTheFileIsMissing() throws Exception {
        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, service());

        assertEquals(List.of(), result.imported());
        assertEquals(List.of(), result.skipped());
        assertEquals(FancyHologramsImport.expectedFile(dir), result.file());
    }

    /**
     * A file in the shape FancyHolograms itself writes: every key flat in the hologram's section,
     * {@code scale_x/y/z}, and the text interval in milliseconds.
     */
    @Test
    void aFancyHologramsFileIsConverted() throws Exception {
        writeImport("""
                holograms:
                  welcome:
                    type: TEXT
                    location:
                      world: world
                      x: 12.5
                      y: 70.0
                      z: -3.5
                      yaw: 90.0
                      pitch: 0.0
                    visibility_distance: 20
                    visibility: ALL
                    persistent: true
                    scale_x: 1.5
                    scale_y: 1.5
                    scale_z: 1.5
                    translation_x: 0.0
                    translation_y: 0.5
                    translation_z: 0.0
                    shadow_radius: 2.0
                    shadow_strength: 0.5
                    block_brightness: 12
                    sky_brightness: 15
                    billboard: vertical
                    text:
                      - "<gold>Welcome"
                      - "<gray>Have fun"
                    text_shadow: true
                    see_through: false
                    text_alignment: left
                    update_text_interval: 2000
                    background: "#112233"
                  shop:
                    type: ITEM
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    item:
                      ==: org.bukkit.inventory.ItemStack
                      v: 3700
                      type: DIAMOND_SWORD
                  modern-item:
                    type: ITEM
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    item:
                      ==: org.bukkit.inventory.ItemStack
                      DataVersion: 4189
                      id: minecraft:golden_apple
                      count: 1
                  statue:
                    type: BLOCK
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    block: DIAMOND_BLOCK
                  vip:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    visibility: PERMISSION_REQUIRED
                    update_text_interval: -1
                    text: ["vip"]
                """);
        HologramService holograms = service();

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms);

        assertEquals(List.of(), result.skipped());
        assertEquals(5, result.imported().size());
        HologramData welcome = holograms.get("welcome");
        assertNotNull(welcome);
        assertEquals(HologramType.TEXT, welcome.type());
        // The translation moves the drawing, so it is added to the position.
        assertEquals(new Pos(12.5, 70.5, -3.5, 90f, 0f), welcome.position());
        assertEquals(List.of("<gold>Welcome", "<gray>Have fun"), welcome.lines());
        assertEquals(TextAlignment.LEFT, welcome.alignment());
        assertEquals(Billboard.VERTICAL, welcome.billboard());
        assertTrue(welcome.textShadow());
        assertEquals(1.5, welcome.scale());
        assertEquals(20, welcome.viewDistance());
        assertEquals(2.0, welcome.shadowRadius());
        assertEquals(0.5, welcome.shadowStrength());
        assertEquals(12, welcome.brightnessBlock());
        assertEquals(15, welcome.brightnessSky());
        // FancyHolograms counts the interval in milliseconds: 2000 ms are 40 ticks.
        assertEquals(40, welcome.updateIntervalTicks());
        assertEquals(0xFF112233, welcome.background());

        assertEquals(Material.DIAMOND_SWORD, holograms.get("shop").item(), "an item as older servers write it");
        assertEquals(Material.GOLDEN_APPLE, holograms.get("modern-item").item(), "an item as newer servers write it");
        assertEquals(Block.DIAMOND_BLOCK, holograms.get("statue").block());
        assertEquals(HologramVisibility.PERMISSION, holograms.get("vip").visibility());
        assertEquals(0, holograms.get("vip").updateIntervalTicks(), "-1 means never");
    }

    @Test
    void anUnevenScaleIsMadeEvenAndReported() throws Exception {
        writeImport("""
                holograms:
                  wide:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    scale_x: 2.0
                    scale_y: 1.0
                    scale_z: 1.0
                    text: ["wide"]
                """);
        HologramService holograms = service();

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms);

        assertEquals(1, result.imported().size());
        assertEquals(1.0, holograms.get("wide").scale());
        assertEquals(1, result.skipped().size(), "the change is reported, not made in silence");
        assertTrue(result.skipped().getFirst().contains("scale"), result.skipped().toString());
    }

    @Test
    void aHologramLinkedToAnNpcBecomesItsNameTag() throws Exception {
        writeImport("""
                holograms:
                  bedwars-name:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 67.0, z: 0.0}
                    linkedNpc: BedWars
                    text:
                      - "<gold>BedWars"
                      - "%group_online_bedwars% playing"
                    background: transparent
                  orphan:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 67.0, z: 0.0}
                    linkedNpc: missing
                    text: ["nobody"]
                """);
        HologramService holograms = service();
        HologramData bedwarsTag = new HologramData("bedwars", HologramType.TEXT, Pos.ZERO, List.of("old"));
        List<String> changed = new ArrayList<>();
        NameTags npcs = new NameTags() {
            @Override
            public HologramData nameTagOf(String npcName) {
                return npcName.equals("bedwars") ? bedwarsTag : null;
            }

            @Override
            public void changed(String npcName) {
                changed.add(npcName);
            }
        };

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms, npcs);

        assertEquals(List.of(), result.imported(), "a linked hologram is not a hologram of its own here");
        assertEquals(List.of("bedwars"), result.nameTags());
        assertEquals(List.of("bedwars"), changed);
        assertEquals(List.of("<gold>BedWars", "%group_online_bedwars% playing"), bedwarsTag.lines());
        assertEquals(0, bedwarsTag.background(), "its look comes along");
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().getFirst().contains("/npc import"),
                "an NPC that does not exist yet is reported with what to do: " + result.skipped());
    }

    @Test
    void entriesThatCannotBeReadAreNamedAndNothingElseIsLost() throws Exception {
        writeImport("""
                holograms:
                  no-location:
                    type: TEXT
                    text: ["Hi"]
                  Weird Name:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                  good:
                    type: TEXT
                    location: {world: world, x: 1.0, y: 65.0, z: 1.0}
                    text: ["Hi"]
                """);
        HologramService holograms = service();

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms);

        assertEquals(List.of("good"), result.imported().stream().map(HologramData::name).toList());
        assertEquals(2, result.skipped().size(), result.skipped().toString());
        assertTrue(result.skipped().getFirst().startsWith("no-location"), result.skipped().toString());
    }

    @Test
    void anExistingHologramIsNeverOverwritten() throws Exception {
        writeImport("""
                holograms:
                  welcome:
                    type: TEXT
                    location: {world: world, x: 0.0, y: 65.0, z: 0.0}
                    text: ["From FancyHolograms"]
                """);
        HologramService holograms = service();
        holograms.create("welcome", HologramType.TEXT, new Pos(5, 65, 5), List.of("Mine"));

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms);

        assertEquals(List.of(), result.imported());
        assertEquals(1, result.skipped().size());
        assertEquals(List.of("Mine"), holograms.get("welcome").lines());
    }
}
