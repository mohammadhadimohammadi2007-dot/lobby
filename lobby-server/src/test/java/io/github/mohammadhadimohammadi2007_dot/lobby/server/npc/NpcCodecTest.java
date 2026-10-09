package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram.Billboard;
import net.minestom.server.color.TeamColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading and writing data/npcs.yml, including a hand-written file. */
class NpcCodecTest {

    @TempDir
    Path dir;

    private YamlDataStore<NpcData> store() {
        return new YamlDataStore<>(dir.resolve("npcs.yml"), "NPCs", new NpcCodec());
    }

    @Test
    void savingAndLoadingKeepsEverything() {
        NpcData data = new NpcData("bedwars", new Pos(1.5, 65, -2.5, 90f, 10f),
                List.of("<gold>BedWars", "%group_online_bedwars% playing", "<yellow>Click to join"));
        data.skin(new NpcSkin(NpcSkin.Kind.PLAYER, "Notch"));
        data.resolvedSkin(new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl"));
        data.turnToPlayer(true);
        data.turnDistance(8);
        data.glowing(true);
        data.glowColor(TeamColor.GOLD);
        data.scale(1.5);
        data.pose(NpcPose.SITTING);
        data.showInTab(true);
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HELMET,
                EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS)) {
            data.equipment(slot, Material.GOLDEN_HELMET);
        }
        data.equipment(EquipmentSlot.MAIN_HAND, Material.RED_BED);
        data.viewDistance(32);
        data.permission("lobby.bedwars");
        data.nameTag().scale(1.25);
        data.nameTag().billboard(Billboard.VERTICAL);
        data.nameTag().background(0);
        data.nameTag().shadowRadius(1);
        data.clickCooldownMillis(750);
        List<Object> any = List.of("connect_group: bedwars",
                Map.of("random", List.of("message: Good luck!", "message: Have fun!")));
        data.actions(NpcTrigger.ANY_CLICK, new NpcData.Actions(any,
                ActionParser.parseList(any, 750, "test", warning -> { })));
        List<Object> right = List.of("open_menu: bedwars");
        data.actions(NpcTrigger.RIGHT_CLICK, new NpcData.Actions(right,
                ActionParser.parseList(right, 750, "test", warning -> { })));

        YamlDataStore<NpcData> store = store();
        store.save(Map.of("bedwars", data));
        store.flush();
        NpcData loaded = store.load().get("bedwars");
        store.close();

        assertNotNull(loaded);
        assertEquals(new Pos(1.5, 65, -2.5, 90f, 10f), loaded.position());
        assertEquals(EntityType.PLAYER, loaded.type());
        assertEquals(List.of("<gold>BedWars", "%group_online_bedwars% playing", "<yellow>Click to join"),
                loaded.nameTag().lines(), "the name tag keeps all its lines");
        assertEquals(1.25, loaded.nameTag().scale());
        assertEquals(Billboard.VERTICAL, loaded.nameTag().billboard());
        assertEquals(0, loaded.nameTag().background());
        assertEquals(1, loaded.nameTag().shadowRadius());
        assertEquals("Notch", loaded.skin().describe());
        assertEquals(new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl"), loaded.resolvedSkin(),
                "the looked-up texture is kept, so a restart needs no lookup");
        assertTrue(loaded.turnToPlayer());
        assertEquals(8, loaded.turnDistance());
        assertTrue(loaded.glowing());
        assertEquals(TeamColor.GOLD, loaded.glowColor());
        assertEquals(1.5, loaded.scale());
        assertEquals(NpcPose.SITTING, loaded.pose());
        assertTrue(loaded.showInTab());
        assertEquals(data.equipment(), loaded.equipment(), "every slot FancyNpcs has, and the rest");
        assertEquals(6, loaded.equipment().size());
        assertEquals(32, loaded.viewDistance());
        assertEquals("lobby.bedwars", loaded.permission());
        assertEquals(750, loaded.clickCooldownMillis());
        // A random: section survives a save as a section, not as text.
        assertEquals(2, loaded.actions(NpcTrigger.ANY_CLICK).parsed().actions().size());
        assertEquals("random", ((Map<?, ?>) loaded.actions(NpcTrigger.ANY_CLICK).entries().get(1))
                .keySet().iterator().next());
        assertEquals(List.of("open_menu: bedwars"), loaded.actions(NpcTrigger.RIGHT_CLICK).entries());
        assertEquals(List.of(), loaded.actions(NpcTrigger.LEFT_CLICK).entries());
        // The name tag sits just above the head of the sitting, 1.5 times bigger NPC.
        assertEquals(65 + (1.8 - NpcPose.SEAT_DROP) * 1.5 + NpcData.NAME_GAP, loaded.nameTag().position().y(), 1e-9);
    }

    @Test
    void aHandWrittenNpcNeedsOnlyAPosition() throws Exception {
        Files.writeString(dir.resolve("npcs.yml"), """
                guide:
                  position: {x: 0.5, y: 65, z: 0.5}
                villager:
                  type: villager
                  position: {x: 3, y: 65, z: 3}
                  name:
                    lines: ["Trader"]
                broken:
                  type: dragon_of_doom
                  position: {x: 0, y: 65, z: 0}
                """);
        YamlDataStore<NpcData> store = store();

        Map<String, NpcData> loaded = store.load();

        assertEquals(EntityType.PLAYER, loaded.get("guide").type(), "player is the default");
        assertEquals(NpcSkin.DEFAULT, loaded.get("guide").skin());
        assertEquals(EntityType.VILLAGER, loaded.get("villager").type());
        assertEquals(List.of("Trader"), loaded.get("villager").nameTag().lines());
        assertEquals(java.util.Set.of("broken"), store.brokenEntries(), "an unknown type is reported by name");
        store.close();
    }

    @Test
    void theHiddenProfileNameFitsAndStays() {
        NpcData data = new NpcData("a-rather-long-npc-name-indeed", Pos.ZERO, List.of());

        assertTrue(data.profileName().length() <= 16, data.profileName());
        assertEquals(data.profileName(), new NpcData("a-rather-long-npc-name-indeed", Pos.ZERO, List.of()).profileName(),
                "the same NPC always has the same profile name");
        assertTrue(!data.profileName().equals(new NpcData("other", Pos.ZERO, List.of()).profileName()));
    }
}
