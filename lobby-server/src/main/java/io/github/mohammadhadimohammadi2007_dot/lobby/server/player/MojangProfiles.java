package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Looks up the UUID of a premium Minecraft account by its name. Blocking: never on the tick thread. */
public final class MojangProfiles {

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int HTTP_OK = 200;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    /**
     * The account's UUID, or empty if no premium account has this name.
     *
     * @throws IOException if Mojang could not be asked (network, rate limit)
     */
    public Optional<UUID> uuidOf(String name) throws IOException {
        if (!VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL + name))
                .timeout(Duration.ofSeconds(4)).GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Mojang answered " + response.statusCode());
        }
        try {
            String id = JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
            return Optional.of(UUID.fromString(id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")));
        } catch (JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException e) {
            throw new IOException("unexpected answer from Mojang: " + e.getMessage(), e);
        }
    }
}
