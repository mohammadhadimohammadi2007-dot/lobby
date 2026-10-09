package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionList;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderScope;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading and writing data/holograms.yml, including files written by hand. */
class HologramCodecTest {

    @TempDir
    Path dir;

    private YamlDataStore<HologramData> store() {
        return new YamlDataStore<>(dir.resolve("holograms.yml"), "Holograms", new HologramCodec());
    }

    private Map<String, HologramData> read(String yaml) throws Exception {
        Files.writeString(dir.resolve("holograms.yml"), yaml);
        return store().load();
    }

    @Test
    void savingAndLoadingKeepsEverything() {
        HologramData data = new HologramData("welcome", HologramType.TEXT, new Pos(1.5, 70, -2.5),
                List.of("<gold>Welcome", "<gray>%player_name%"));
        data.frames(List.of(List.of("<gold>Welcome", "<gray>%player_name%"), List.of("second frame")));
        data.scale(1.5);
        data.billboard(Billboard.VERTICAL);
        data.alignment(TextAlignment.LEFT);
        data.background(0xFF102030);
        data.textShadow(true);
        data.seeThrough(true);
        data.viewDistance(24);
        data.updateIntervalTicks(40);
        data.permission("lobby.vip");
        data.lineSpacing(0.3);
        List<Object> actionLines = List.of("message: hello", "sound: entity.experience_orb.pickup");
        data.actions(actionLines, ActionParser.parseList(actionLines, 250, "test", warning -> { }));

        YamlDataStore<HologramData> store = store();
        store.save(Map.of("welcome", data));
        store.flush();
        HologramData loaded = store.load().get("welcome");

        assertNotNull(loaded);
        assertEquals("welcome", loaded.name());
        assertEquals(HologramType.TEXT, loaded.type());
        assertEquals(new Pos(1.5, 70, -2.5), loaded.position());
        assertEquals(List.of("<gold>Welcome", "<gray>%player_name%"), loaded.lines());
        assertEquals(2, loaded.frames().size(), "the animation frames survive");
        assertEquals(List.of("second frame"), loaded.frames().get(1));
        assertEquals(1.5, loaded.scale());
        assertEquals(Billboard.VERTICAL, loaded.billboard());
        assertEquals(TextAlignment.LEFT, loaded.alignment());
        assertEquals(0xFF102030, loaded.background());
        assertTrue(loaded.textShadow());
        assertTrue(loaded.seeThrough());
        assertEquals(24, loaded.viewDistance());
        assertEquals(40, loaded.updateIntervalTicks());
        assertEquals("lobby.vip", loaded.permission());
        assertEquals(0.3, loaded.lineSpacing());
        assertEquals(actionLines, loaded.actionEntries(), "action lines are kept exactly as written");
        assertEquals(2, loaded.actions().actions().size());
        assertEquals(250, loaded.actions().cooldownMillis());
    }

    @Test
    void aHandWrittenMinimalHologramWorks() throws Exception {
        Map<String, HologramData> loaded = read("""
                welcome:
                  position:
                    x: 0.5
                    y: 65
                    z: 0.5
                  lines:
                    - "Hello"
                """);

        HologramData data = loaded.get("welcome");
        assertNotNull(data);
        assertEquals(HologramType.TEXT, data.type(), "text is the default type");
        assertEquals(List.of("Hello"), data.lines());
        assertEquals(1, data.frames().size());
        assertEquals(Billboard.CENTER, data.billboard());
        assertNull(data.background(), "no background means Minecraft's own");
        assertEquals(HologramData.DEFAULT_VIEW_DISTANCE, data.viewDistance());
        assertEquals(ActionList.EMPTY.actions(), data.actions().actions());
    }

    @Test
    void namesAreLowerCasedAndBrokenEntriesAreSkipped() throws Exception {
        YamlDataStore<HologramData> store = store();
        Files.writeString(dir.resolve("holograms.yml"), """
                Welcome:
                  position: {x: 0, y: 65, z: 0}
                  lines: ["Hi"]
                no-position:
                  lines: ["Hi"]
                item-without-item:
                  type: item
                  position: {x: 0, y: 65, z: 0}
                good-item:
                  type: item
                  position: {x: 0, y: 65, z: 0}
                  item: diamond_sword
                """);

        Map<String, HologramData> loaded = store.load();

        assertEquals(List.of("welcome", "good-item"), loaded.values().stream().map(HologramData::name).toList());
        assertEquals(java.util.Set.of("no-position", "item-without-item"), store.brokenEntries());
        assertEquals(Material.DIAMOND_SWORD, loaded.get("good-item").item());
    }

    @Test
    void backgroundAcceptsTheWordsAndHexColours() throws Exception {
        Map<String, HologramData> loaded = read("""
                a:
                  position: {x: 0, y: 65, z: 0}
                  background: transparent
                b:
                  position: {x: 0, y: 65, z: 0}
                  background: "#112233"
                c:
                  position: {x: 0, y: 65, z: 0}
                  background: "#80112233"
                d:
                  position: {x: 0, y: 65, z: 0}
                  background: "not a colour"
                """);

        assertEquals(0, loaded.get("a").background());
        assertEquals(0xFF112233, loaded.get("b").background(), "a colour without alpha is opaque");
        assertEquals(0x80112233, loaded.get("c").background());
        assertNull(loaded.get("d").background(), "an unreadable colour falls back to the default");
    }

    @Test
    void aBlockHologramNeedsABlockAndKeepsIt() throws Exception {
        Map<String, HologramData> loaded = read("""
                statue:
                  type: block
                  position: {x: 0, y: 65, z: 0}
                  block: diamond_block
                """);

        assertEquals(Block.DIAMOND_BLOCK, loaded.get("statue").block());
    }

    @Test
    void updateIntervalFollowsTheTextWhenNotSet() throws Exception {
        Map<String, HologramData> loaded = read("""
                still:
                  position: {x: 0, y: 65, z: 0}
                  lines: ["Welcome"]
                live:
                  position: {x: 0, y: 65, z: 0}
                  lines: ["Players: %server_online%"]
                fixed:
                  position: {x: 0, y: 65, z: 0}
                  lines: ["Players: %server_online%"]
                  update-interval: 100
                """);

        // The hologram service classifies the text; the codec only reads what was written.
        loaded.get("live").textScope(PlaceholderScope.GLOBAL);
        loaded.get("fixed").textScope(PlaceholderScope.GLOBAL);
        assertEquals(0, loaded.get("still").effectiveUpdateIntervalTicks(), "text that cannot change needs no rebuild");
        assertEquals(HologramData.PLACEHOLDER_UPDATE_TICKS, loaded.get("live").effectiveUpdateIntervalTicks());
        assertEquals(100, loaded.get("fixed").effectiveUpdateIntervalTicks());
    }
}
