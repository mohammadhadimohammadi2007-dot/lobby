package io.github.mohammadhadimohammadi2007_dot.lobby.server.movement;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OperatorPermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.player.GameProfile;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Double jump, /fly, jump pads and launch pads on a running server, with the bundled movement.yml. */
@EnvTest
class MovementEnvTest {

    private static final Pos SPAWN = new Pos(0.5, 41, 0.5, 0, 0);

    @TempDir
    Path dir;

    private record Setup(MovementService movement, ConfigManager config) {
    }

    private Setup start(Env env, String extraMovement) throws Exception {
        ConfigManager config = new ConfigManager(dir);
        config.load();
        if (extraMovement != null) {
            Path file = dir.resolve(ConfigManager.MOVEMENT_FILE);
            Files.writeString(file, Files.readString(file).replace("launch-pads: []", extraMovement));
            config.load();
        }
        RegionTracker regions = new RegionTracker();
        MovementService movement = new MovementService(config, new OperatorPermissionService(config), regions);
        EventNode<PlayerEvent> node = EventNode.type("movement-test-" + UUID.randomUUID(), EventFilter.PLAYER);
        env.process().eventHandler().addChild(node);
        regions.register(node);
        movement.register(node);
        return new Setup(movement, config);
    }

    private static Player join(Env env, Instance instance) {
        Player player = env.createConnection(new GameProfile(UUID.randomUUID(), "Steve")).connect(instance, SPAWN);
        player.setGameMode(GameMode.ADVENTURE);
        env.tick();
        return player;
    }

    @Test
    void theBundledFileLoadsWithoutWarnings(Env env) throws Exception {
        Setup setup = start(env, null);
        assertEquals(List.of(), setup.config().current().warnings().stream()
                .filter(warning -> warning.startsWith("movement.yml")).toList());
        assertEquals(2, setup.config().current().movement().jumpPads().size());
    }

    @Test
    void aDoubleJumpThrowsThePlayerAndTurnsFlightOff(Env env) throws Exception {
        Setup setup = start(env, null);
        Player player = join(env, env.createFlatInstance());
        assertTrue(player.isAllowFlying(), "on the ground the second jump is ready");

        env.process().eventHandler().call(new PlayerStartFlyingEvent(player));

        assertFalse(player.isFlying());
        assertFalse(player.isAllowFlying(), "no second double jump before landing");
        assertTrue(player.getVelocity().y() > 0, "thrown up: " + player.getVelocity());
        assertTrue(player.getVelocity().z() > 0, "and forward, the way they look (yaw 0 is +z): " + player.getVelocity());
    }

    @Test
    void flyTurnsDoubleJumpOff(Env env) throws Exception {
        Setup setup = start(env, null);
        Player player = join(env, env.createFlatInstance());

        assertTrue(setup.movement().toggleFly(player));
        env.process().eventHandler().call(new PlayerStartFlyingEvent(player));

        assertTrue(player.isFlying(), "with /fly on, flying is flying");
        assertFalse(setup.movement().toggleFly(player));
        assertFalse(player.isFlying());
    }

    @Test
    void aPlateOnASlimeBlockIsAJumpPad(Env env) throws Exception {
        Setup setup = start(env, null);
        Instance instance = env.createFlatInstance();
        Player player = join(env, instance);
        Pos pad = SPAWN.add(3, 0, 0);
        instance.setBlock(pad.sub(0, 1, 0), Block.SLIME_BLOCK);
        instance.setBlock(pad, Block.LIGHT_WEIGHTED_PRESSURE_PLATE);

        env.process().eventHandler().call(new PlayerMoveEvent(player, pad, true));

        assertTrue(player.getVelocity().y() > 0, "thrown: " + player.getVelocity());
        assertTrue(setup.movement() != null);
    }

    @Test
    void aLaunchPadThrowsEveryoneTheSameWay(Env env) throws Exception {
        Setup setup = start(env, """
                launch-pads:
                  - name: tower
                    from: [5, 41, 5]
                    to: [6, 41, 6]
                    velocity: [0, 30, -20]
                """);
        Player player = join(env, env.createFlatInstance());

        env.process().eventHandler().call(new PlayerMoveEvent(player, new Pos(5.5, 41, 5.5), true));

        assertEquals(30, player.getVelocity().y(), 0.01);
        assertEquals(-20, player.getVelocity().z(), 0.01);
        assertEquals(1, setup.movement().launchPads().size());
    }
}
