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
 * <p>Reading a skin that is already on MineSkin ({@code GET /get/uuid/<uuid>}) needs no API key (checked
 * against MineSkin's own API description). Turning an image link into a skin ({@code POST /v2/generate})
 * needs one; without it, image links are refused with an explanation. The key is only ever sent to
 * MineSkin, and never logged.
 */
public final class NpcSkins {

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcSkins.class);
    private static final String MINESKIN_URL = "https://api.mineskin.org/get/uuid/";
    private static final String MINESKIN_GENERATE_URL = "https://api.mineskin.org/v2/generate";
    /** MineSkin has a skin made by Mojang's servers, which can take a while. */
    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(60);
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    /** What an admin is told when they use an image link without a key. */
    public static final String NO_KEY_EXPLANATION = "a link to a skin image has to be uploaded to MineSkin"
            + " first, and that needs a MineSkin API key (integrations.yml, mineskin.api-key; free at"
            + " https://mineskin.org/apikey). Without a key, upload it on mineskin.org yourself and use that"
            + " skin's mineskin.org link instead";
    /** MineSkin asks every client to name itself. */
    private static final String USER_AGENT = "MinestomLobby/1.0 (+https://github.com/mohammadhadimohammadi2007-dot/lobby)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int HTTP_OK = 200;

    private final @Nullable MojangSkinFetcher mojang;
    private final @Nullable SkinsRestorerReader skinsRestorer;
    private final String mineSkinKey;
    private final URI generateUrl;
    private final HttpClient http;

    /**
     * @param mojang        Mojang lookups, or {@code null} to not use Mojang
     * @param skinsRestorer the SkinsRestorer database, or {@code null} if that integration is off
     */
    public NpcSkins(@Nullable MojangSkinFetcher mojang, @Nullable SkinsRestorerReader skinsRestorer) {
        this(mojang, skinsRestorer, "");
    }

    /** @param mineSkinKey a MineSkin API key, or {@code ""} for none (image links are then refused) */
    public NpcSkins(@Nullable MojangSkinFetcher mojang, @Nullable SkinsRestorerReader skinsRestorer,
                    String mineSkinKey) {
        this(mojang, skinsRestorer, mineSkinKey, URI.create(MINESKIN_GENERATE_URL));
    }

    /** @param generateUrl where images are uploaded; only tests change it */
    NpcSkins(@Nullable MojangSkinFetcher mojang, @Nullable SkinsRestorerReader skinsRestorer, String mineSkinKey,
             URI generateUrl) {
        this.generateUrl = generateUrl;
        this.mojang = mojang;
        this.skinsRestorer = skinsRestorer;
        this.mineSkinKey = mineSkinKey.strip();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** True if image links can be turned into skins, because a MineSkin API key is set. */
    public boolean canUpload() {
        return !mineSkinKey.isEmpty();
    }

    /**
     * What a lookup found.
     *
     * @param problem     what went wrong, in words for an admin; empty when it worked
     * @param replacement where the skin should be looked up from now on, or {@code null} to keep it: an
     *                    uploaded image becomes {@code mineskin:<uuid>}, so it is never uploaded twice
     */
    public record Result(@Nullable PlayerSkin skin, String problem, @Nullable NpcSkin replacement) {

        public Result(@Nullable PlayerSkin skin, String problem) {
            this(skin, problem, null);
        }

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
            case IMAGE -> canUpload() ? Async.supply(() -> uploadToMineSkin(skin.source()))
                    : CompletableFuture.completedFuture(Result.missing(NO_KEY_EXPLANATION));
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

    /** Has MineSkin turn an image link into a skin. Blocking; runs on a virtual thread. */
    private Result uploadToMineSkin(String imageUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("url", imageUrl);
        HttpRequest request = HttpRequest.newBuilder(generateUrl)
                .timeout(GENERATE_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer " + mineSkinKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == HTTP_TOO_MANY_REQUESTS) {
                return Result.missing("MineSkin says too many skins were made with this key just now; try again"
                        + " in a minute");
            }
            Optional<Generated> generated = parseGenerated(response.body());
            if (response.statusCode() != HTTP_OK || generated.isEmpty()) {
                return Result.missing("MineSkin could not make a skin from " + imageUrl + " (HTTP "
                        + response.statusCode() + "): " + errorOf(response.body()));
            }
            Generated skin = generated.get();
            return new Result(skin.texture().toPlayerSkin(), "", new NpcSkin(NpcSkin.Kind.MINESKIN, skin.uuid()));
        } catch (IOException e) {
            LOGGER.warn("Could not reach MineSkin: {}", e.getMessage());
            return Result.missing("MineSkin could not be reached: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.missing("interrupted");
        }
    }

    /** A skin MineSkin made: its id there and its texture. */
    record Generated(String uuid, SkinData texture) {
    }

    /** {@code skin.uuid} and {@code skin.texture.data.value|signature} of a {@code /v2/generate} answer. */
    static Optional<Generated> parseGenerated(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                return Optional.empty();
            }
            JsonObject skin = root.getAsJsonObject("skin");
            JsonObject data = skin.getAsJsonObject("texture").getAsJsonObject("data");
            if (!skin.has("uuid") || !data.has("value") || !data.has("signature")) {
                return Optional.empty();
            }
            return Optional.of(new Generated(skin.get("uuid").getAsString().replace("-", ""),
                    new SkinData(data.get("value").getAsString(), data.get("signature").getAsString())));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The first error message in a MineSkin answer, or the start of the answer if it has none. */
    static String errorOf(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("errors") && root.get("errors").isJsonArray() && !root.getAsJsonArray("errors").isEmpty()) {
                JsonObject first = root.getAsJsonArray("errors").get(0).getAsJsonObject();
                return first.has("message") ? first.get("message").getAsString() : first.toString();
            }
            if (root.has("error")) {
                return root.get("error").getAsString();
            }
        } catch (RuntimeException e) {
            // Not JSON: show the start of it below.
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
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
