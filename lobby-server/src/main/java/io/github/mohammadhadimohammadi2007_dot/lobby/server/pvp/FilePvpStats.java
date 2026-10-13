package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PvP records in {@code data/pvp-stats.json}, used when there is no database. The whole file is kept in memory
 * (a few dozen bytes per player) and written again after each change.
 */
public final class FilePvpStats implements PvpStatsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<List<PvpStats>>() { }.getType();

    private final Path file;
    private Map<UUID, PvpStats> all;

    public FilePvpStats(Path dataDir) {
        this.file = dataDir.resolve("data").resolve("pvp-stats.json");
    }

    private synchronized Map<UUID, PvpStats> all() throws IOException {
        if (all == null) {
            all = new LinkedHashMap<>();
            if (Files.isRegularFile(file)) {
                try {
                    List<PvpStats> stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
                    if (stored != null) {
                        stored.forEach(stats -> all.put(stats.id(), stats));
                    }
                } catch (JsonParseException e) {
                    throw new IOException("Broken stats file " + file + ": " + e.getMessage(), e);
                }
            }
        }
        return all;
    }

    @Override
    public synchronized Optional<PvpStats> load(UUID player) throws IOException {
        return Optional.ofNullable(all().get(player));
    }

    @Override
    public synchronized void save(PvpStats stats) throws IOException {
        all().put(stats.id(), stats);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(List.copyOf(all.values()), LIST), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public synchronized List<PvpStats> top(PvpStats.Board board, int limit) throws IOException {
        return all().values().stream()
                .filter(stats -> board.of(stats) > 0)
                .sorted(Comparator.comparingInt(board::of).reversed().thenComparing(PvpStats::name))
                .limit(limit)
                .toList();
    }

    @Override
    public String describe() {
        return "file " + file;
    }
}