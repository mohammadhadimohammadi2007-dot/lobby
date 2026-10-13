package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * Players' PvP records: kept in memory while they are online, saved in the background after every change,
 * and the leaderboards, read again every leaderboard-refresh seconds.
 */
public final class PvpStatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PvpStatsService.class);

    private record Board(List<PvpStats> entries, long readMillis) {
    }

    private final PvpStatsStore store;
    private final LongSupplier clock;
    private final Map<UUID, PvpStats> online = new ConcurrentHashMap<>();
    private final Map<PvpStats.Board, Board> boards = new EnumMap<>(PvpStats.Board.class);
    private final Map<PvpStats.Board, Boolean> refreshing = new ConcurrentHashMap<>();
    private volatile Consumer<UUID> onChange = id -> { };

    public PvpStatsService(PvpStatsStore store, LongSupplier clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Called with a player's id after their record changed (the placeholders forget their old values). */
    public void onChange(Consumer<UUID> listener) {
        onChange = listener;
    }

    public String describe() {
        return store.describe();
    }

    /** Reads a joining player's record in the background. */
    public CompletableFuture<Void> load(Player player) {
        UUID id = player.getUuid();
        String name = player.getUsername();
        return Async.supply(() -> {
            try {
                return store.load(id).orElse(PvpStats.none(id, name));
            } catch (Exception e) {
                LOGGER.error("Could not read the PvP record of {}: {}", name, e.getMessage());
                return PvpStats.none(id, name);
            }
        }).thenAccept(stats -> {
            online.putIfAbsent(id, stats);
            onChange.accept(id);
        });
    }

    public void forget(UUID player) {
        online.remove(player);
    }

    /** The record of an online player (all zero until it is read). */
    public PvpStats get(UUID player, String name) {
        return online.getOrDefault(player, PvpStats.none(player, name));
    }

    /** Any player's record, online or not. */
    public CompletableFuture<PvpStats> stats(UUID player) {
        PvpStats known = online.get(player);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        return Async.supply(() -> {
            try {
                return store.load(player).orElse(PvpStats.none(player, ""));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read the PvP record: " + e.getMessage(), e);
            }
        });
    }

    /** Changes a player's record and saves it in the background. */
    public PvpStats update(UUID player, String name, UnaryOperator<PvpStats> change) {
        PvpStats updated = change.apply(get(player, name));
        online.put(player, updated);
        onChange.accept(player);
        Async.run(() -> {
            try {
                store.save(updated);
            } catch (Exception e) {
                LOGGER.error("Could not save the PvP record of {}: {}", name, e.getMessage());
            }
        });
        return updated;
    }

    /**
     * The leaderboard as last read; read again in the background once it is older than {@code maxAgeMillis}.
     * Empty until the first read finished.
     */
    public List<PvpStats> top(PvpStats.Board board, int size, long maxAgeMillis) {
        Board current;
        synchronized (boards) {
            current = boards.get(board);
        }
        long now = clock.getAsLong();
        boolean stale = current == null || now - current.readMillis() >= maxAgeMillis;
        if (stale && refreshing.putIfAbsent(board, true) == null) {
            Async.run(() -> {
                try {
                    List<PvpStats> entries = store.top(board, size);
                    synchronized (boards) {
                        boards.put(board, new Board(List.copyOf(entries), clock.getAsLong()));
                    }
                } catch (Exception e) {
                    LOGGER.error("Could not read the PvP leaderboard: {}", e.getMessage());
                } finally {
                    refreshing.remove(board);
                }
            });
        }
        return current == null ? List.of() : current.entries();
    }
}