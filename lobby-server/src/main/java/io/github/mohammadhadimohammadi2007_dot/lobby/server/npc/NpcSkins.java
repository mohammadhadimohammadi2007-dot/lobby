package io.github.mohammadhadimohammadi2007_dot.lobby.server.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.MojangSkinFetcher;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinData;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins.SkinsRestorerReader;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.entity.PlayerSkin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Looks up the texture of an NPC skin: from Mojang, from SkinsRestorer's database or from MineSkin. All
 * of it runs on a virtual thread, never on the tick thread.
 *
 * <p>MineSkin is only read: {@code GET /get/uuid/<uuid>} needs no API key (checked against MineSkin's
 * own API description). Turning an image link into a skin would need one, which is why
 * {@link NpcSkin#parse} refuses plain image links.
 */
public final class NpcSkins {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcSkins.class);
    private static final String MINESKIN_URL = "https://api.mineskin.org/get/uuid/";
    /** MineSkin asks every client to name itself. */
    private static final String USER_AGENT = "MinestomLobby/1.0 (+https://github.com/mohammadhadimohammadi2007-dot/lobby)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int HTTP_OK = 200;

    private final @Nullable MojangSkinFetcher mojang;
    private final @Nullable SkinsRestorerReader skinsRestorer;
    private final HttpClient http;

    /**
     * @param mojang        Mojang lookups, or {@code null} to not use Mojang
     * @param skinsRestorer the SkinsRestorer database, or {@code null} if that integration is off
     */
    public NpcSkins(@Nullable MojangSkinFetcher mojang, @Nullable SkinsRestorerReader skinsRestorer) {
        this.mojang = mojang;
        this.skinsRestorer = skinsRestorer;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** What went wrong with a lookup, in words for an admin. Empty when it worked. */
    public record Result(@Nullable PlayerSkin skin, String problem) {

        static Result found(SkinData skin) {
            return new Result(skin.toPlayerSkin(), "");
        }

        static Result missing(String problem) {
            return new Result(null, problem);
        }

        public boolean found() {
            return skin != null;
        }
    }

    /**
     * Looks up a skin. Kinds that need no lookup ({@code @none}, {@code @mirror}, {@code @texture})
     * complete right away with no skin and no problem.
     */
    public CompletableFuture<Result> resolve(NpcSkin skin) {
        return switch (skin.kind()) {
            case DEFAULT, MIRROR, TEXTURE -> CompletableFuture.completedFuture(new Result(null, ""));
            case PLAYER -> Async.supply(() -> fromMojang(skin.source()));
            case SKINSRESTORER -> fromSkinsRestorer(skin.source());
            case MINESKIN -> Async.supply(() -> fromMineSkin(skin.source()));
        };
    }

    private Result fromMojang(String name) {
        if (mojang == null) {
            return Result.missing("looking up skins from Mojang is turned off");
        }
        return mojang.fetch(name).map(Result::found)
                .orElseGet(() -> Result.missing("there is no premium account called " + name
                        + ", or Mojang did not answer"));
    }

    private CompletableFuture<Result> fromSkinsRestorer(String name) {
        if (skinsRestorer == null) {
            return CompletableFuture.completedFuture(Result.missing(
                    "the SkinsRestorer integration is off (integrations.yml)"));
        }
        return skinsRestorer.customSkin(name).thenApply(found -> found.map(Result::found)
                .orElseGet(() -> Result.missing("SkinsRestorer has no custom skin called " + name)));
    }

    private Result fromMineSkin(String uuid) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MINESKIN_URL + uuid))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HTTP_OK) {
                return Result.missing("MineSkin answered HTTP " + response.statusCode() + " for " + uuid);
            }
            return parseMineSkin(response.body()).map(Result::found)
                    .orElseGet(() -> Result.missing("MineSkin's answer for " + uuid + " had no texture"));
        } catch (IOException e) {
            LOGGER.warn("Could not reach MineSkin: {}", e.getMessage());
            return Result.missing("MineSkin could not be reached: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.missing("interrupted");
        }
    }

    /** {@code data.texture.value} and {@code data.texture.signature} of MineSkin's answer. */
    static Optional<SkinData> parseMineSkin(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject texture = root.getAsJsonObject("data").getAsJsonObject("texture");
            if (texture == null || !texture.has("value") || !texture.has("signature")) {
                return Optional.empty();
            }
            return Optional.of(new SkinData(texture.get("value").getAsString(), texture.get("signature").getAsString()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
