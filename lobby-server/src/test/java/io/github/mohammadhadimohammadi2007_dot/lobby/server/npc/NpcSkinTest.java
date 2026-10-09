package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinData;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skin words an admin can type, and reading MineSkin's answer. */
class NpcSkinTest {

    @Test
    void theFancyNpcsWordsWork() throws Exception {
        assertEquals(NpcSkin.DEFAULT, NpcSkin.parse("@none"));
        assertEquals(NpcSkin.MIRROR, NpcSkin.parse("@mirror"));
        assertEquals(new NpcSkin(NpcSkin.Kind.PLAYER, "Notch"), NpcSkin.parse("Notch"));
    }

    @Test
    void skinsRestorerAndMineSkin() throws Exception {
        assertEquals(new NpcSkin(NpcSkin.Kind.SKINSRESTORER, "knight"), NpcSkin.parse("sr:knight"));
        String uuid = "5d8fcbe06e0a419bb985b53c2c8f01dc";
        assertEquals(new NpcSkin(NpcSkin.Kind.MINESKIN, uuid), NpcSkin.parse("mineskin:" + uuid));
        // A link to the skin's page works too, with or without dashes in the uuid.
        assertEquals(new NpcSkin(NpcSkin.Kind.MINESKIN, uuid),
                NpcSkin.parse("https://mineskin.org/5d8fcbe0-6e0a-419b-b985-b53c2c8f01dc"));
        assertEquals(new NpcSkin(NpcSkin.Kind.MINESKIN, uuid), NpcSkin.parse("https://mineskin.org/skins/" + uuid));
    }

    @Test
    void anImageLinkIsRefusedWithTheReason() {
        NpcSkin.SkinException refused = assertThrows(NpcSkin.SkinException.class,
                () -> NpcSkin.parse("https://example.com/skin.png"));
        assertTrue(refused.getMessage().contains("API key"), refused.getMessage());
        assertThrows(NpcSkin.SkinException.class, () -> NpcSkin.parse("not a name!"));
    }

    @Test
    void describeGivesBackWhatWasTyped() throws Exception {
        for (String typed : new String[] {"@none", "@mirror", "Notch", "sr:knight",
                "mineskin:5d8fcbe06e0a419bb985b53c2c8f01dc"}) {
            assertEquals(typed, NpcSkin.parse(typed).describe());
        }
        assertTrue(NpcSkin.parse("Notch").fetched());
        assertFalse(NpcSkin.MIRROR.fetched());
    }

    @Test
    void mineSkinsAnswerIsRead() {
        // The shape of api.mineskin.org/get/uuid/<uuid>, cut down to what matters.
        String answer = """
                {"uuid":"5d8f","data":{"uuid":"x","texture":{"value":"dGV4dHVyZQ==","signature":"c2lnbmF0dXJl",
                "url":"http://textures.minecraft.net/texture/abc"}}}""";

        assertEquals(Optional.of(new SkinData("dGV4dHVyZQ==", "c2lnbmF0dXJl")), NpcSkins.parseMineSkin(answer));
        assertEquals(Optional.empty(), NpcSkins.parseMineSkin("{\"error\":\"not found\"}"));
        assertEquals(Optional.empty(), NpcSkins.parseMineSkin("not json"));
    }
}
