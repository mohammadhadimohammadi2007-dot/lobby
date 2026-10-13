package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where PvP records are kept: the shared database, or a file when there is none. Every method blocks. */
public interface PvpStatsStore {

    Optional<PvpStats> load(UUID player) throws Exception;

    void save(PvpStats stats) throws Exception;

    /** The best {@code limit} players by {@code board}, best first; players with 0 are left out. */
    List<PvpStats> top(PvpStats.Board board, int limit) throws Exception;

    /** For logs. */
    String describe();
}