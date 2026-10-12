package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * One jar in the addons folder and what its {@code addon.json} says, or why it cannot be read.
 *
 * @param description {@code null} if {@code error} is set
 * @param error       what is wrong with the jar or its addon.json, in plain words
 */
public record AddonFile(Path jar, @Nullable AddonDescription description, @Nullable String error) {

    /** The file inside the jar that describes the addon. */
    public static final String DESCRIPTION_FILE = "addon.json";

    /** Reads the description from a jar. Never throws: problems become {@link #error()}. */
    public static AddonFile read(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry(DESCRIPTION_FILE);
            if (entry == null) {
                return failed(jar, "there is no " + DESCRIPTION_FILE + " in the jar (is it an addon for this lobby?)");
            }
            try (InputStream in = file.getInputStream(entry)) {
                return new AddonFile(jar, parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)), null);
            }
        } catch (DescriptionException e) {
            return failed(jar, DESCRIPTION_FILE + ": " + e.getMessage());
        } catch (IOException e) {
            return failed(jar, "cannot read the jar: " + e.getMessage());
        }
    }

    private static AddonFile failed(Path jar, String error) {
        return new AddonFile(jar, null, error);
    }

    /** A mistake in addon.json. */
    static final class DescriptionException extends Exception {
        DescriptionException(String message) {
            super(message);
        }
    }

    /** Reads addon.json. Package-private for tests. */
    static AddonDescription parse(String json) throws DescriptionException {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new DescriptionException("expected an object { ... }");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new DescriptionException("not valid JSON (" + e.getMessage() + ")");
        }
        String id = required(root, "id").toLowerCase(Locale.ROOT);
        if (!id.matches(AddonDescription.ID_PATTERN)) {
            throw new DescriptionException("'id' must be lowercase letters, digits and -, found '" + id + "'");
        }
        String name = optional(root, "name", id);
        String version = required(root, "version");
        String main = required(root, "main");
        String apiVersion = required(root, "api-version");
        return new AddonDescription(id, name, version, main, apiVersion, list(root, "authors"),
                ids(root, "depends"), ids(root, "soft-depends"));
    }

    private static String required(JsonObject root, String key) throws DescriptionException {
        String value = optional(root, key, null);
        if (value == null || value.isBlank()) {
            throw new DescriptionException("'" + key + "' is missing");
        }
        return value.strip();
    }

    private static String optional(JsonObject root, String key, @Nullable String def) throws DescriptionException {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return def;
        }
        if (!value.isJsonPrimitive()) {
            throw new DescriptionException("'" + key + "' must be text");
        }
        return value.getAsString();
    }

    private static List<String> list(JsonObject root, String key) throws DescriptionException {
        JsonElement value = root.get(key);
        List<String> items = new ArrayList<>();
        if (value == null || value.isJsonNull()) {
            return items;
        }
        if (!value.isJsonArray()) {
            throw new DescriptionException("'" + key + "' must be a list like [\"a\", \"b\"]");
        }
        for (JsonElement item : (JsonArray) value) {
            if (!item.isJsonPrimitive()) {
                throw new DescriptionException("'" + key + "' must only hold text");
            }
            items.add(item.getAsString().strip());
        }
        return items;
    }

    private static List<String> ids(JsonObject root, String key) throws DescriptionException {
        List<String> ids = new ArrayList<>();
        for (String id : list(root, key)) {
            ids.add(id.toLowerCase(Locale.ROOT));
        }
        return ids;
    }
}
