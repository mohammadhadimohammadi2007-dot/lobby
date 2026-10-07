package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Looks up the skin of a premium account by name from Mojang. Results (including "no such account")
 * are cached so each name is only looked up once in a while. Blocking: call off the tick thread.
 */
public final class MojangSkinFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MojangSkinFetcher.class);
    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CACHE_TIME = Duration.ofHours(6);
    private static final int HTTP_OK = 200;
    /** When the cache holds more names than this, expired entries are removed. */
    private static final int CACHE_CLEANUP_SIZE = 5_000;
    /** Valid Minecraft usernames; anything else cannot be a premium account. */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final Map<String, CachedSkin> cache = new ConcurrentHashMap<>();

    /** The skin of the premium account called {@code username}, if there is one. */
    public Optional<SkinData> fetch(String username) {
        if (!VALID_NAME.matcher(username).matches()) {
            return Optional.empty();
        }
        String key = username.toLowerCase(Locale.ROOT);
        CachedSkin cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis() > System.currentTimeMillis()) {
            return cached.skin();
        }
        try {
            Optional<SkinData> skin = lookup(username);
            if (cache.size() > CACHE_CLEANUP_SIZE) {
                long now = System.currentTimeMillis();
                cache.values().removeIf(entry -> entry.expiresAtMillis() <= now);
            }
            cache.put(key, new CachedSkin(skin, System.currentTimeMillis() + CACHE_TIME.toMillis()));
            return skin;
        } catch (IOException e) {
            LOGGER.warn("Could not fetch the skin for {} from Mojang: {}", username, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<SkinData> lookup(String username) throws IOException, InterruptedException {
        Optional<JsonObject> profile = getJson(PROFILE_URL + username);
        if (profile.isEmpty() || !profile.get().has("id")) {
            return Optional.empty(); // Not a premium account.
        }
        String id = profile.get().get("id").getAsString();
        Optional<JsonObject> session = getJson(String.format(SESSION_URL, id));
        if (session.isEmpty() || !session.get().has("properties")) {
            return Optional.empty();
        }
        for (JsonElement element : session.get().getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (SkinData.TEXTURES_PROPERTY.equals(property.get("name").getAsString()) && property.has("signature")) {
                return Optional.of(new SkinData(property.get("value").getAsString(), property.get("signature").getAsString()));
            }
        }
        return Optional.empty();
    }

    /** GETs a JSON object. Empty for "not found" answers; throws for network errors and rate limits. */
    private Optional<JsonObject> getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HTTP_OK) {
            return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
        }
        if (response.statusCode() >= 500 || response.statusCode() == 429) {
            throw new IOException("Mojang answered HTTP " + response.statusCode());
        }
        return Optional.empty();
    }

    private record CachedSkin(Optional<SkinData> skin, long expiresAtMillis) {
    }
}
