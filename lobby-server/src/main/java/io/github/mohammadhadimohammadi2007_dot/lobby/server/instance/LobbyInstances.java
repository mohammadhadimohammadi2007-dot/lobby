package io.github.mohammadhadimohammadi2007_dot.lobby.server.instance;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.LobbySwitcher;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.SpawnListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldRules;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The lobby instances of this server.
 *
 * <p>All of them show the same map: with more than one lobby, each is a {@code SharedInstance} of the
 * loaded map, so the chunks exist once in memory no matter how many lobbies there are. Players only see
 * players, entities and chat (in instance channels) of their own lobby, because Minestom tracks entities
 * per instance.
 *
 * <p>With {@code lobbies.instances: 1}, the default, there is exactly one instance: the loaded map
 * itself, exactly like a server without this feature.
 */
public final class LobbyInstances implements LobbyInstanceInfo, LobbySwitcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyInstances.class);
    /**
     * How much of a tick may be spent moving players to another lobby. Changing instance has to happen
     * on the tick thread and costs a few milliseconds per player, because the client is sent the whole
     * world again: moving a full lobby of 200 players at once stalled the server for 300 ms. With a
     * budget the rate follows the machine instead of a guessed number, and a tick keeps most of its
     * 50 ms for everything else. One player is always moved, so a single switch never waits.
     */
    private static final long MOVE_BUDGET_NANOS = 5_000_000L;

    /** One player waiting to be moved. */
    private record PendingMove(Player player, Instance target, int number, Pos spawn) {
    }

    private final List<Instance> instances;
    private final ConfigManager config;
    private final LobbyText text;
    private final PermissionService permissions;
    private final Queue<PendingMove> pendingMoves = new ConcurrentLinkedQueue<>();

    private LobbyInstances(List<Instance> instances, ConfigManager config, LobbyText text,
                           PermissionService permissions) {
        this.instances = List.copyOf(instances);
        this.config = config;
        this.text = text;
        this.permissions = permissions;
    }

    /**
     * Creates the instances described by {@code lobbies:} in config.yml and applies the world rules to
     * each of them. Call this once, before players can join.
     */
    public static LobbyInstances create(InstanceContainer map, ConfigManager config, LobbyText text,
                                        PermissionService permissions) {
        LobbyConfig settings = config.current().config();
        int wanted = settings.lobbies().instanceCount(settings.server().maxPlayers());
        List<Instance> created = new ArrayList<>(wanted);
        if (wanted == 1) {
            created.add(map);
        } else {
            for (int number = 1; number <= wanted; number++) {
                created.add(MinecraftServer.getInstanceManager().createSharedInstance(map));
            }
        }
        LobbyInstances lobbies = new LobbyInstances(created, config, text, permissions);
        lobbies.applyWorldRules(settings.world());
        lobbies.startMoving();
        if (wanted > 1) {
            LOGGER.info("Lobbies: {} instances of the same map, new players join the {} one ({} players each)",
                    wanted, settings.lobbies().join().configName(),
                    settings.lobbies().playersPerInstance());
        }
        return lobbies;
    }

    /** Every instance, in order; index 0 is lobby 1. */
    public List<Instance> all() {
        return instances;
    }

    @Override
    public int count() {
        return instances.size();
    }

    /** The instance with that number (1-based), or {@code null} if there is none. */
    public @Nullable Instance byNumber(int number) {
        return number >= 1 && number <= instances.size() ? instances.get(number - 1) : null;
    }

    @Override
    public int numberOf(Player player) {
        return numberOf(player.getInstance());
    }

    /** The number (1-based) of an instance, or 0 if it is not a lobby instance. */
    public int numberOf(@Nullable Instance instance) {
        return instance == null ? 0 : instances.indexOf(instance) + 1;
    }

    @Override
    public int onlineIn(int number) {
        Instance instance = byNumber(number);
        return instance == null ? 0 : instance.getPlayers().size();
    }

    /** The instance a joining player should spawn in, following {@code lobbies.join}. */
    public Instance forJoin() {
        LobbyConfig.Lobbies settings = config.current().config().lobbies();
        if (instances.size() == 1) {
            return instances.getFirst();
        }
        int limit = settings.playersPerInstance();
        if (settings.join() == JoinStrategy.FILL_FIRST) {
            for (Instance instance : instances) {
                if (instance.getPlayers().size() < limit) {
                    return instance;
                }
            }
            // Every instance is at its limit: the server's own max-players decides, so use the emptiest.
            return emptiest();
        }
        return emptiest();
    }

    private Instance emptiest() {
        Instance best = instances.getFirst();
        int fewest = best.getPlayers().size();
        for (Instance instance : instances) {
            int players = instance.getPlayers().size();
            if (players < fewest) {
                best = instance;
                fewest = players;
            }
        }
        return best;
    }

    /**
     * Moves a player to another lobby, telling them what happened. Safe to call from any thread: the
     * move itself runs on the tick thread.
     */
    @Override
    public void switchTo(Player player, int number) {
        Instance target = byNumber(number);
        if (target == null) {
            player.sendMessage(text.message(MessageKey.LOBBY_UNKNOWN, player,
                    Messages.text("number", number), Messages.text("count", count())));
            return;
        }
        if (target == player.getInstance()) {
            player.sendMessage(text.message(MessageKey.LOBBY_ALREADY_HERE, player, Messages.text("number", number)));
            return;
        }
        int limit = config.current().config().lobbies().playersPerInstance();
        if (target.getPlayers().size() >= limit && !permissions.hasPermission(player, Permissions.LOBBY_JOIN_FULL)) {
            player.sendMessage(text.message(MessageKey.LOBBY_FULL, player, Messages.text("number", number)));
            return;
        }
        Pos spawn = SpawnListener.spawnPosition(config.current().config());
        // Queued instead of moved right away: see MOVES_PER_TICK.
        pendingMoves.add(new PendingMove(player, target, number, spawn));
    }

    /** Moves a few waiting players every tick, so a full lobby never stalls one. */
    private void startMoving() {
        MinecraftServer.getSchedulerManager().buildTask(this::moveSome)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    /** Runs on the tick thread: moves waiting players until {@link #MOVE_BUDGET_NANOS} is used up. */
    private void moveSome() {
        long deadline = System.nanoTime() + MOVE_BUDGET_NANOS;
        do {
            PendingMove move = pendingMoves.poll();
            if (move == null) {
                return;
            }
            Player player = move.player();
            // Left, or already there because they asked twice: that costs nothing, so it is not counted.
            if (!player.isOnline() || player.getInstance() == move.target()) {
                continue;
            }
            player.setRespawnPoint(move.spawn());
            player.setInstance(move.target(), move.spawn()).thenRun(() -> player.sendMessage(
                    text.message(MessageKey.LOBBY_SWITCHED, player, Messages.text("number", move.number()))));
        } while (System.nanoTime() < deadline);
    }

    /** How many players are still waiting to be moved. For tests and metrics. */
    public int pendingMoveCount() {
        return pendingMoves.size();
    }

    /** Applies time of day and weather to every instance. Called at startup and after a reload. */
    public void applyWorldRules(LobbyConfig.World settings) {
        for (Instance instance : instances) {
            WorldRules.apply(instance, settings);
        }
    }
}
