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

    @Test
    void aFancyHologramsFileIsConverted() throws Exception {
        writeImport("""
                holograms:
                  welcome:
                    location:
                      world: world
                      x: 12.5
                      y: 70.0
                      z: -3.5
                    type: TEXT
                    data:
                      text:
                        - "<gold>Welcome"
                        - "<gray>Have fun"
                      text_alignment: LEFT
                      text_shadow: true
                      billboard: VERTICAL
                      scale: 1.5
                      visibility_distance: 20
                      update_text_interval: 2
                      background: "#112233"
                  shop:
                    location: {x: 0.0, y: 65.0, z: 0.0}
                    type: ITEM
                    data:
                      item: diamond_sword
                """);
        HologramService holograms = service();

        FancyHologramsImport.Result result = FancyHologramsImport.run(dir, holograms);

        assertEquals(List.of(), result.skipped());
        assertEquals(2, result.imported().size());
        HologramData welcome = holograms.get("welcome");
        assertNotNull(welcome);
        assertEquals(HologramType.TEXT, welcome.type());
        assertEquals(new Pos(12.5, 70, -3.5), welcome.position());
        assertEquals(List.of("<gold>Welcome", "<gray>Have fun"), welcome.lines());
        assertEquals(TextAlignment.LEFT, welcome.alignment());
        assertEquals(Billboard.VERTICAL, welcome.billboard());
        assertTrue(welcome.textShadow());
        assertEquals(1.5, welcome.scale());
        assertEquals(20, welcome.viewDistance());
        // FancyHolograms counts that interval in seconds, this lobby in ticks.
        assertEquals(40, welcome.updateIntervalTicks());
        assertEquals(0xFF112233, welcome.background());

        HologramData shop = holograms.get("shop");
        assertNotNull(shop);
        assertEquals(HologramType.ITEM, shop.type());
        assertEquals(Material.DIAMOND_SWORD, shop.item());
    }

    @Test
    void entriesThatCannotBeReadAreNamedAndNothingElseIsLost() throws Exception {
        writeImport("""
                holograms:
                  no-location:
                    type: TEXT
                    data:
                      text: ["Hi"]
                  Weird Name:
                    location: {x: 0.0, y: 65.0, z: 0.0}
                  good:
                    location: {x: 1.0, y: 65.0, z: 1.0}
                    data:
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
                welcome:
                  location: {x: 0.0, y: 65.0, z: 0.0}
                  data:
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
