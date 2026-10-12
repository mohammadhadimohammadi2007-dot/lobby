package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addons' per-player values as small JSON files, {@code data/addon-settings/<addon>/<uuid>.json}, used when
 * there is no database (standalone servers).
 */
public final class FileAddonSettings implements AddonSettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP = new TypeToken<Map<String, String>>() { }.getType();

    private final Path folder;
    /** One lock per file, so two saves for the same player never lose each other's value. */
    private final Map<Path, Object> locks = new ConcurrentHashMap<>();

    public FileAddonSettings(Path dataDir) {
        this.folder = dataDir.resolve("data").resolve("addon-settings");
    }

    @Override
    public Optional<String> load(UUID player, String addon, String key) throws IOException {
        Path file = file(player, addon);
        synchronized (lock(file)) {
            return Optional.ofNullable(read(file).get(key));
        }
    }

    @Override
    public void save(UUID player, String addon, String key, @Nullable String value) throws IOException {
        Path file = file(player, addon);
        synchronized (lock(file)) {
            Map<String, String> values = read(file);
            if (value == null) {
                values.remove(key);
            } else {
                values.put(key, value);
            }
            if (values.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(values, MAP), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public String describe() {
        return "files in " + folder;
    }

    private Object lock(Path file) {
        return locks.computeIfAbsent(file, ignored -> new Object());
    }

    private static Map<String, String> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new TreeMap<>();
        }
        try {
            Map<String, String> values = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
            return values == null ? new TreeMap<>() : new TreeMap<>(values);
        } catch (JsonParseException e) {
            throw new IOException("Broken settings file " + file + ": " + e.getMessage(), e);
        }
    }

    private Path file(UUID player, String addon) {
        return folder.resolve(addon).resolve(player + ".json");
    }
}
