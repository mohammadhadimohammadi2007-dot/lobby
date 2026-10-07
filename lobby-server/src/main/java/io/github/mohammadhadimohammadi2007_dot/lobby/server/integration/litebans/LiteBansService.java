package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database.DatabasePool;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Connections;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.timer.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads bans and mutes from the LiteBans tables.
 *
 * <ul>
 *   <li>Standalone mode: banned players are refused at login (there is no proxy to do it).</li>
 *   <li>Every mode: each online player's active mute is cached on join. Every few seconds new mute rows
 *       are read for online players, and cached mutes are re-checked so unmutes apply too.</li>
 * </ul>
 * If the database is unreachable, players are let in and nobody counts as muted; errors are logged.
 */
public final class LiteBansService implements MuteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiteBansService.class);
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DatabasePool database;
    private final LiteBansQueries queries;
    private final ConfigManager configManager;
    private final Duration checkInterval;

    /** Online players: id to IP address, used to match new IP mutes. */
    private final Map<UUID, String> onlineIps = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();
    private final AtomicBoolean pollRunning = new AtomicBoolean();
    private volatile long lastMuteId;
    private Task pollTask;

    private LiteBansService(DatabasePool database, IntegrationsConfig.LiteBans settings, ConfigManager configManager) {
        this.database = database;
        this.queries = new LiteBansQueries(settings.tablePrefix(), settings.serverName());
        this.configManager = configManager;
        this.checkInterval = Duration.ofSeconds(settings.checkIntervalSeconds());
    }

    /**
     * Checks the LiteBans tables and starts polling. Call off the tick thread (it queries the database).
     *
     * @throws SQLException with a readable message if the tables are missing
     */
    public static LiteBansService start(DatabasePool database, IntegrationsConfig.LiteBans settings,
                                        ConfigManager configManager) throws SQLException {
        LiteBansService service = new LiteBansService(database, settings, configManager);
        service.lastMuteId = database.queryNow(connection -> {
            service.queries.checkTables(connection);
            return service.queries.latestMuteId(connection);
        });
        service.pollTask = MinecraftServer.getSchedulerManager()
                .buildTask(() -> Async.run(service::poll))
                .delay(service.checkInterval)
                .repeat(service.checkInterval)
                .schedule();
        return service;
    }

    /**
     * Registers the login and join listeners.
     *
     * @param checkBans true in standalone mode, where nobody else checks bans
     */
    public void register(EventNode<Event> node, boolean checkBans) {
        if (checkBans) {
            node.addListener(AsyncPlayerPreLoginEvent.class, this::checkBan);
        }
        node.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            if (event.isFirstConfig()) {
                loadMute(event.getPlayer().getUuid(), Connections.ip(event.getPlayer().getPlayerConnection()));
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            onlineIps.remove(event.getPlayer().getUuid());
            mutes.remove(event.getPlayer().getUuid());
        });
    }

    @Override
    public Optional<Punishment> activeMute(UUID playerId) {
        Punishment mute = mutes.get(playerId);
        if (mute == null || !mute.activeAt(System.currentTimeMillis())) {
            return Optional.empty();
        }
        return Optional.of(mute);
    }

    /** Runs inside the async login event, so a short blocking query is fine here. */
    private void checkBan(AsyncPlayerPreLoginEvent event) {
        String ip = Connections.ip(event.getConnection());
        Optional<Punishment> ban;
        try {
            ban = database.queryNow(connection ->
                    queries.activeBan(connection, event.getPlayerUuid(), ip, System.currentTimeMillis()));
        } catch (SQLException e) {
            LOGGER.error("Could not check bans for {}, letting them in: {}", event.getUsername(), e.getMessage());
            return;
        }
        ban.ifPresent(punishment -> {
            LOGGER.info("Refused banned player {} (ban #{})", event.getUsername(), punishment.id());
            Messages messages = configManager.current().messages();
            String expires = punishment.permanent()
                    ? messages.template(MessageKey.BAN_NEVER_EXPIRES)
                    : EXPIRY_FORMAT.format(Instant.ofEpochMilli(punishment.untilMillis()));
            event.getConnection().kick(messages.render(MessageKey.KICK_BANNED,
                    Messages.text("reason", punishment.reason()),
                    Messages.text("banned-by", punishment.punishedBy()),
                    Messages.text("expires", expires)));
        });
    }

    /** Runs inside the async configuration event. */
    private void loadMute(UUID playerId, String ip) {
        onlineIps.put(playerId, ip);
        try {
            Optional<Punishment> mute = database.queryNow(connection ->
                    queries.activeMute(connection, playerId, ip, System.currentTimeMillis()));
            // The player may have left while the query ran; do not keep a mute for someone offline.
            if (mute.isPresent() && onlineIps.containsKey(playerId)) {
                mutes.put(playerId, mute.get());
            }
        } catch (SQLException e) {
            LOGGER.error("Could not load the mute of {}: {}", playerId, e.getMessage());
        }
    }

    /** Picks up new mutes and drops removed or expired ones. Runs on a virtual thread. */
    private void poll() {
        if (!pollRunning.compareAndSet(false, true)) {
            return; // The previous poll is still running (slow database).
        }
        try {
            long now = System.currentTimeMillis();
            database.queryNow(connection -> {
                List<LiteBansQueries.MuteRow> rows = queries.newMutes(connection, lastMuteId, now);
                for (LiteBansQueries.MuteRow row : rows) {
                    lastMuteId = Math.max(lastMuteId, row.punishment().id());
                    applyNewMute(row);
                }
                List<Long> cachedIds = mutes.values().stream().map(Punishment::id).distinct().toList();
                var stillActive = queries.stillActiveMutes(connection, cachedIds, now);
                mutes.values().removeIf(mute -> !stillActive.contains(mute.id()));
                return null;
            });
        } catch (SQLException e) {
            LOGGER.warn("Could not check LiteBans for mute changes: {}", e.getMessage());
        } finally {
            pollRunning.set(false);
        }
    }

    private void applyNewMute(LiteBansQueries.MuteRow row) {
        onlineIps.forEach((playerId, ip) -> {
            boolean sameAccount = playerId.toString().equalsIgnoreCase(row.uuid());
            boolean sameIp = row.punishment().ipBased() && ip.equals(row.ip());
            if (sameAccount || sameIp) {
                mutes.put(playerId, row.punishment());
            }
        });
    }

    /** Stops polling. The pool itself is closed by its owner. */
    public void shutdown() {
        if (pollTask != null) {
            pollTask.cancel();
        }
    }
}
