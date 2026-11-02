package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.particle.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cosmetics.yml: the bundled file, and mistakes in it. */
class CosmeticsConfigTest {

    @TempDir
    Path dir;

    @Test
    void theBundledFileLoadsWithoutWarnings() throws Exception {
        ConfigSnapshot snapshot = new ConfigManager(dir).load();
        assertEquals(List.of(), snapshot.warnings().stream().filter(w -> w.startsWith("cosmetics.yml")).toList());
        CosmeticsConfig config = snapshot.cosmetics();
        assertTrue(config.enabled());
        assertEquals(12, config.of(CosmeticType.TRAIL).size());
        assertEquals(10, config.of(CosmeticType.HAT).size());
        assertEquals(8, config.of(CosmeticType.GADGET).size());
        assertEquals(10, config.of(CosmeticType.PET).size());
        assertEquals(6, config.of(CosmeticType.BALLOON).size());
        assertEquals(6, config.of(CosmeticType.OUTFIT).size());

        Cosmetic flame = config.find(CosmeticType.TRAIL, "flame");
        assertNotNull(flame);
        assertEquals("", flame.permission(), "free for everyone");
        assertEquals("lobby.cosmetic.trail.notes", config.find(CosmeticType.TRAIL, "notes").permission());
        assertEquals(107, config.find(CosmeticType.TRAIL, "sparkle").minProtocol(), "min-version 1.9");
        assertEquals(0, flame.minProtocol());
        assertInstanceOf(Particle.Dust.class, config.find(CosmeticType.TRAIL, "pink").trail().particle());

        Cosmetic cake = config.find(CosmeticType.HAT, "cake");
        assertEquals(Material.PLAYER_HEAD, cake.hat().material());
        assertNotNull(cake.hat().profile());
        assertNull(config.find(CosmeticType.HAT, "glass").hat().profile());

        Cosmetic leap = config.find(CosmeticType.GADGET, "leap");
        assertEquals(Cosmetic.GadgetType.LEAP, leap.gadget().type());
        assertEquals(6000, leap.gadget().cooldownMillis());
        Cosmetic paintball = config.find(CosmeticType.GADGET, "paintball");
        assertEquals(6, paintball.gadget().blocks().size());
        assertEquals(5000, paintball.gadget().durationMillis());
        assertEquals(Material.SNOWBALL, paintball.icon(), "each type has its own icon");

        Cosmetic.Trail rain = config.find(CosmeticType.TRAIL, "rain").trail();
        assertEquals(Cosmetic.TrailStyle.CLOUD, rain.style());
        assertNotNull(rain.drip());

        Cosmetic.Pet dog = config.find(CosmeticType.PET, "dog").pet();
        assertEquals(EntityType.WOLF, dog.entity());
        assertTrue(dog.baby());
        assertFalse(dog.nameTag().isEmpty());
        assertEquals(477, config.find(CosmeticType.PET, "fox").minProtocol(), "min-version 1.14");

        Cosmetic.Balloon red = config.find(CosmeticType.BALLOON, "red").balloon();
        assertEquals(Material.RED_WOOL, red.look().material());
        assertTrue(red.leash());

        Cosmetic.Outfit ocean = config.find(CosmeticType.OUTFIT, "ocean").outfit();
        assertEquals(4, ocean.pieces().size());
        assertNotNull(ocean.color());
        assertEquals(Material.DIAMOND_CHESTPLATE,
                config.find(CosmeticType.OUTFIT, "diamond").outfit().pieces().get(EquipmentSlot.CHESTPLATE));
    }

    @Test
    void newKindsReportTheirMistakes() throws Exception {
        Files.writeString(dir.resolve(ConfigManager.COSMETICS_FILE), """
                trails:
                  cloud:
                    particle: cloud
                    style: cloud
                    drip: dripping_water
                  dripless:
                    particle: flame
                    drip: dripping_water
                pets:
                  ok:
                    entity: pig
                  stand:
                    entity: armor_stand
                  rock:
                    entity: stone
                  grown:
                    entity: zombie
                    baby: false
                  cow:
                    entity: cow
                    baby: false
                balloons:
                  head:
                    material: player_head
                    head-owner: MHF_Cake
                    leash: false
                outfits:
                  empty:
                    name: nothing
                  gold:
                    chest: golden_chestplate
                    color: "#ff0000"
                gadgets:
                  paint:
                    type: paintball
                    blocks: [red_wool, air, not_a_block]
                  far:
                    type: grapple
                    range: 500
                """);
        ConfigSnapshot snapshot = new ConfigManager(dir).load();
        CosmeticsConfig config = snapshot.cosmetics();
        assertEquals(List.of("cloud", "dripless"), config.of(CosmeticType.TRAIL).stream().map(Cosmetic::id).toList());
        assertNull(config.find(CosmeticType.TRAIL, "dripless").trail().drip(), "drip only for clouds");
        assertEquals(List.of("ok", "grown", "cow"), config.of(CosmeticType.PET).stream().map(Cosmetic::id).toList());
        assertFalse(config.find(CosmeticType.PET, "cow").pet().baby());
        assertFalse(config.find(CosmeticType.BALLOON, "head").balloon().leash());
        assertEquals(List.of("gold"), config.of(CosmeticType.OUTFIT).stream().map(Cosmetic::id).toList());
        assertEquals(List.of(Block.RED_WOOL), config.find(CosmeticType.GADGET, "paint").gadget().blocks());
        assertEquals(25, config.find(CosmeticType.GADGET, "far").gadget().range(), "too far: the default");
        List<String> warnings = snapshot.warnings().stream().filter(w -> w.startsWith("cosmetics.yml")).toList();
        for (String option : List.of("trails.dripless.drip", "pets.stand.entity", "pets.rock.entity", "outfits.empty",
                "outfits.gold.color", "gadgets.paint.blocks", "gadgets.far.range")) {
            assertTrue(warnings.stream().anyMatch(w -> w.contains(option)), option + " in " + warnings);
        }
    }

    @Test
    void aBrokenCosmeticIsNamedAndLeftOutTheOthersStay() throws Exception {
        Files.writeString(dir.resolve(ConfigManager.COSMETICS_FILE), """
                trails:
                  good:
                    particle: flame
                  badparticle:
                    particle: fire_works_typo
                  badstyle:
                    particle: flame
                    style: tornado
                  badversion:
                    particle: flame
                    min-version: latest
                  "Bad Name":
                    particle: flame
                hats:
                  stone:
                    material: stone
                    head-owner: Notch
                gadgets:
                  boom:
                    type: rocket
                  ok:
                    type: burst
                    cooldown: 5
                """);
        ConfigSnapshot snapshot = new ConfigManager(dir).load();
        CosmeticsConfig config = snapshot.cosmetics();
        assertEquals(List.of("good"), config.of(CosmeticType.TRAIL).stream().map(Cosmetic::id).toList());
        assertEquals(List.of("stone"), config.of(CosmeticType.HAT).stream().map(Cosmetic::id).toList());
        assertEquals(List.of("ok"), config.of(CosmeticType.GADGET).stream().map(Cosmetic::id).toList());
        List<String> warnings = snapshot.warnings().stream().filter(w -> w.startsWith("cosmetics.yml")).toList();
        for (String option : List.of("trails.badparticle.particle", "trails.badstyle.style", "trails.badversion.min-version",
                "trails.Bad Name", "hats.stone", "gadgets.boom.type")) {
            assertTrue(warnings.stream().anyMatch(w -> w.contains(option)), option + " in " + warnings);
        }
    }
}
