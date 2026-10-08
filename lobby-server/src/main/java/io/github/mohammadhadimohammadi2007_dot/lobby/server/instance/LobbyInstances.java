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
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.world.WorldRules;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    private final List<Instance> instances;
    private final ConfigManager config;
    private final LobbyText text;
    private final PermissionService permissions;

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
        Async.onTickThread(() -> {
            if (!player.isOnline()) {
                return;
            }
            player.setRespawnPoint(spawn);
            player.setInstance(target, spawn).thenRun(() -> player.sendMessage(
                    text.message(MessageKey.LOBBY_SWITCHED, player, Messages.text("number", number))));
        });
    }

    /** Applies time of day and weather to every instance. Called at startup and after a reload. */
    public void applyWorldRules(LobbyConfig.World settings) {
        for (Instance instance : instances) {
            WorldRules.apply(instance, settings);
        }
    }
}
