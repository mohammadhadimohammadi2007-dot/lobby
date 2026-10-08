package io.github.mohammadhadimohammadi2007_dot.lobby.server.hologram;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.MetadataDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The look of a hologram: rotation, light, shadow and visibility mode. These are the properties
 * FancyHolograms has, and they are only sent when they differ from what Minecraft does by itself.
 */
class HologramLookTest {

    private static final Pos AT = new Pos(10.5, 70, -4.5);

    @TempDir
    Path dir;

    private static HologramData hologram() {
        return new HologramData("welcome", HologramType.TEXT, AT, List.of("Hi"));
    }

    private static Map<Integer, net.minestom.server.entity.Metadata.Entry<?>> metadata(HologramData data) {
        return HologramParts.modern(data, List.of(Component.text("Hi"))).getFirst().metadata();
    }

    @Test
    void rotationGoesIntoThePosition() {
        HologramData data = hologram();

        assertNull(HologramProperties.apply(data, "rotate", "90").error());
        assertNull(HologramProperties.apply(data, "rotate-pitch", "45").error());

        assertEquals(90f, data.position().yaw());
        assertEquals(45f, data.position().pitch());
        // The entity is spawned facing that way.
        assertEquals(90f, HologramParts.modern(data, List.of(Component.text("Hi"))).getFirst().position().yaw());
        // FancyHolograms spells it rotatepitch; both work.
        assertNull(HologramProperties.apply(data, "rotatepitch", "-30").error());
        assertEquals(-30f, data.position().pitch());
        // A pitch beyond straight down is clamped.
        HologramProperties.apply(data, "rotate-pitch", "200");
        assertEquals(90f, data.position().pitch());
    }

    @Test
    void brightnessIsOnlySentWhenItWasSet() {
        HologramData data = hologram();
        int index = MetadataDef.Display.BRIGHTNESS_OVERRIDE.index();

        assertNull(metadata(data).get(index), "by default a hologram is lit like any other entity");

        assertNull(HologramProperties.apply(data, "brightness", "block 7").error());
        assertEquals(7, data.brightnessBlock());
        assertEquals(HologramData.LIGHT_FROM_WORLD, data.brightnessSky());
        // Minecraft packs block light at bit 4 and sky light at bit 20; an unset level becomes full light.
        assertEquals((7 << 4) | (15 << 20), metadata(data).get(index).value());

        assertNull(HologramProperties.apply(data, "brightness", "sky 3").error());
        assertEquals((7 << 4) | (3 << 20), metadata(data).get(index).value());

        assertNull(HologramProperties.apply(data, "brightness", "default").error());
        assertNull(metadata(data).get(index));
        assertNotNull(HologramProperties.apply(data, "brightness", "block 99").error(), "16 is too bright");
        assertNotNull(HologramProperties.apply(data, "brightness", "moon 3").error());
    }

    @Test
    void theShadowIsOffUntilItHasARadius() {
        HologramData data = hologram();
        int radius = MetadataDef.Display.SHADOW_RADIUS.index();
        int strength = MetadataDef.Display.SHADOW_STRENGTH.index();

        assertNull(metadata(data).get(radius));

        assertNull(HologramProperties.apply(data, "shadowradius", "2.5").error());
        assertNull(HologramProperties.apply(data, "shadowstrength", "0.5").error());

        assertEquals(2.5f, metadata(data).get(radius).value());
        assertEquals(0.5f, metadata(data).get(strength).value());
    }

    @Test
    void everythingIsSavedAndReadBackAgain() {
        HologramData data = hologram();
        data.position(AT.withYaw(45f).withPitch(10f));
        data.brightness(5, 9);
        data.shadowRadius(1.5);
        data.shadowStrength(0.25);
        data.visibility(HologramVisibility.MANUAL);

        YamlDataStore<HologramData> store = new YamlDataStore<>(dir.resolve("holograms.yml"), "Holograms",
                new HologramCodec());
        store.save(Map.of("welcome", data));
        store.flush();
        HologramData loaded = store.load().get("welcome");
        store.close();

        assertNotNull(loaded);
        assertEquals(45f, loaded.position().yaw());
        assertEquals(10f, loaded.position().pitch());
        assertEquals(5, loaded.brightnessBlock());
        assertEquals(9, loaded.brightnessSky());
        assertEquals(1.5, loaded.shadowRadius());
        assertEquals(0.25, loaded.shadowStrength());
        assertEquals(HologramVisibility.MANUAL, loaded.visibility());
        assertEquals(List.of(), List.copyOf(loaded.manualViewers()), "who was shown it is not saved");
    }
}
