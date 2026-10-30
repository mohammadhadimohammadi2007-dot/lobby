package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigSnapshot;
import net.minestom.server.item.Material;
import net.minestom.server.particle.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(5, config.of(CosmeticType.TRAIL).size());
        assertEquals(3, config.of(CosmeticType.HAT).size());
        assertEquals(3, config.of(CosmeticType.GADGET).size());

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
                    style: wings
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
