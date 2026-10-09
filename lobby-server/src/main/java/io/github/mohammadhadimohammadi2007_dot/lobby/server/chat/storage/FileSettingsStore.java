package io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player chat settings as small JSON files in {@code data/chat-settings/}, used when there is no database
 * (standalone servers). One file per player who changed something.
 */
public final class FileSettingsStore implements SettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path folder;

    public FileSettingsStore(Path dataDir) {
        this.folder = dataDir.resolve("data").resolve("chat-settings");
    }

    /** The JSON shape on disk; kept separate so the format stays readable and stable. */
    private record Stored(Boolean chatVisible, Boolean mentions, Boolean persian, String channel, List<String> ignored,
                          String visibility) {
    }

    @Override
    public Optional<PlayerChatSettings> load(UUID player) throws IOException {
        Path file = file(player);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (stored == null) {
                return Optional.empty();
            }
            PlayerChatSettings settings = PlayerChatSettings.DEFAULTS;
            settings = settings.withChatVisible(stored.chatVisible() == null || stored.chatVisible());
            settings = settings.withMentions(stored.mentions() == null || stored.mentions());
            if (stored.persian() != null) {
                settings = settings.withPersian(stored.persian());
            }
            settings = settings.withChannel(stored.channel());
            settings = settings.withVisibility(stored.visibility());
            if (stored.ignored() != null) {
                for (String id : stored.ignored()) {
                    try {
                        settings = settings.withIgnored(UUID.fromString(id), true);
                    } catch (IllegalArgumentException badEntry) {
                        // Skip anything that is not a UUID.
                    }
                }
            }
            return Optional.of(settings);
        } catch (JsonParseException e) {
            throw new IOException("Broken settings file " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(UUID player, PlayerChatSettings settings) throws IOException {
        Files.createDirectories(folder);
        Stored stored = new Stored(settings.chatVisible(), settings.mentions(), settings.persian(), settings.channel(),
                settings.ignored().stream().map(UUID::toString).toList(), settings.visibility());
        Path file = file(player);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(stored), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String describe() {
        return "files in " + folder;
    }

    private Path file(UUID player) {
        return folder.resolve(player + ".json");
    }
}
