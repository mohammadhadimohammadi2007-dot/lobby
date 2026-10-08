package io.github.mohammadhadimohammadi2007_dot.lobby.server.player;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;

import java.util.function.Supplier;

/** Puts joining players at spawn in adventure mode and catches players who fall into the void. */
public final class SpawnListener {

    private final ConfigManager configManager;
    private final Supplier<Instance> joinInstance;

    /**
     * @param joinInstance which lobby instance a joining player spawns in, asked once per player
     *                     (see {@code LobbyInstances#forJoin()})
     */
    public SpawnListener(ConfigManager configManager, Supplier<Instance> joinInstance) {
        this.configManager = configManager;
        this.joinInstance = joinInstance;
    }

    /** The spawn point from config.yml as a position. */
    public static Pos spawnPosition(LobbyConfig config) {
        LobbyConfig.SpawnPoint spawn = config.spawn();
        return new Pos(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    /** Registers the listeners on {@code node}. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(joinInstance.get());
            event.getPlayer().setRespawnPoint(spawnPosition(configManager.current().config()));
        });
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                event.getPlayer().setGameMode(GameMode.ADVENTURE);
            }
        });
        node.addListener(PlayerMoveEvent.class, event -> {
            LobbyConfig config = configManager.current().config();
            if (event.getNewPosition().y() < config.world().voidY()) {
                teleportToSpawn(event.getPlayer(), config);
            }
        });
    }

    /** Sends a player to the spawn point from config.yml. */
    public static void teleportToSpawn(Player player, LobbyConfig config) {
        Pos spawn = spawnPosition(config);
        player.setRespawnPoint(spawn);
        player.teleport(spawn);
    }
}
