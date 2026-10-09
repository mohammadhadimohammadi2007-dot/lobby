package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import com.sun.net.httpserver.HttpServer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinData;
import net.minestom.server.entity.PlayerSkin;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void anImageLinkIsAnImageAndNeedsAKey() throws Exception {
        NpcSkin image = NpcSkin.parse("https://example.com/skin.png");
        assertEquals(new NpcSkin(NpcSkin.Kind.IMAGE, "https://example.com/skin.png"), image);
        assertTrue(image.fetched());
        assertThrows(NpcSkin.SkinException.class, () -> NpcSkin.parse("not a name!"));

        NpcSkins noKey = new NpcSkins(null, null);
        assertFalse(noKey.canUpload());
        NpcSkins.Result refused = noKey.resolve(image).get(5, TimeUnit.SECONDS);
        assertFalse(refused.found());
        assertTrue(refused.problem().contains("API key"), refused.problem());
    }

    @Test
    void withAKeyAnImageIsUploadedOnceAndBecomesAMineSkinSkin() throws Exception {
        // A stand-in for api.mineskin.org/v2/generate, answering in the shape the real one does.
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/generate", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = """
                    {"success":true,"skin":{"uuid":"62d016b0155742219969a81ea9854954","shortId":"cc416f6d",
                    "visibility":"public","variant":"classic","texture":{"data":{"value":"dGV4dHVyZQ==",
                    "signature":"c2lnbmF0dXJl"}}}}""".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        try {
            URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/generate");
            NpcSkins skins = new NpcSkins(null, null, "test-key", url);
            assertTrue(skins.canUpload());

            NpcSkins.Result result = skins.resolve(NpcSkin.parse("https://example.com/skin.png"))
                    .get(10, TimeUnit.SECONDS);

            assertEquals("Bearer test-key", auth.get());
            assertTrue(body.get().contains("https://example.com/skin.png"), body.get());
            assertEquals(new PlayerSkin("dGV4dHVyZQ==", "c2lnbmF0dXJl"), result.skin());
            assertEquals(new NpcSkin(NpcSkin.Kind.MINESKIN, "62d016b0155742219969a81ea9854954"),
                    result.replacement(), "stored as mineskin:<uuid> so it is never uploaded again");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mineSkinsErrorsAreShownAsTheySayThem() {
        assertEquals(Optional.empty(), NpcSkins.parseGenerated("""
                {"success":false,"errors":[{"code":"invalid_image","message":"Invalid image"}]}"""));
        assertEquals("Invalid image", NpcSkins.errorOf("""
                {"success":false,"errors":[{"code":"invalid_image","message":"Invalid image"}]}"""));
        assertEquals("not json", NpcSkins.errorOf("not json"));
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
