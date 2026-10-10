package io.github.mohammadhadimohammadi2007_dot.lobby.server.movement;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double jump, {@code /fly}, jump pads and launch pads from {@code movement.yml}.
 *
 * <p>Double jump works the usual way: players on the ground may fly, and the moment they start flying
 * (pressing jump in the air) flight is turned off again and they are thrown in their look direction.
 * Players in creative or spectator mode, and players who turned on {@code /fly}, are left alone.
 *
 * <p>Everything here runs in movement events on the tick thread and only looks at blocks when a player
 * enters another block, so standing still or walking inside one block costs nothing.
 */
public final class MovementService implements RegionListener {

    /** A pad does not throw the same player again within this time (they land on it again). */
    private static final long PAD_COOLDOWN_MILLIS = 500;
    private static final int PARTICLE_COUNT = 20;
    private static final float PARTICLE_SPEED = 0.05f;
    private static final Vec PARTICLE_SPREAD = new Vec(0.3, 0.1, 0.3);

    private final ConfigManager config;
    private final PermissionService permissions;
    private final RegionTracker regions;
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDoubleJump = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPad = new ConcurrentHashMap<>();

    public MovementService(ConfigManager config, PermissionService permissions, RegionTracker regions) {
        this.config = config;
        this.permissions = permissions;
        this.regions = regions;
    }

    private MovementConfig settings() {
        return config.current().movement();
    }

    public void register(EventNode<PlayerEvent> node) {
        regions.listen(MovementConfig.LAUNCH_PAD_OWNER, this);
        applyLaunchPads();
        node.addListener(PlayerSpawnEvent.class, event -> allowDoubleJump(event.getPlayer()));
        node.addListener(PlayerGameModeChangeEvent.class, event -> {
            // Creative and spectator players fly on their own; everyone else needs it set up again.
            flying.remove(event.getPlayer().getUuid());
            Player player = event.getPlayer();
            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> allowDoubleJump(player));
        });
        node.addListener(PlayerStartFlyingEvent.class, event -> doubleJump(event.getPlayer()));
        node.addListener(PlayerMoveEvent.class, this::onMove);
        node.addListener(PlayerDisconnectEvent.class, event -> {
            UUID id = event.getPlayer().getUuid();
            flying.remove(id);
            lastDoubleJump.remove(id);
            lastPad.remove(id);
        });
    }

    /** Applies a reloaded movement.yml: the launch pads change, and double jump may have been turned off. */
    public void reload() {
        applyLaunchPads();
    }

    private void applyLaunchPads() {
        regions.setRegions(MovementConfig.LAUNCH_PAD_OWNER,
                settings().launchPads().stream().map(MovementConfig.LaunchPad::region).toList());
    }

    /** Turns {@code /fly} on or off for a player. Returns the new state. */
    public boolean toggleFly(Player player) {
        boolean enable = flying.add(player.getUuid());
        if (!enable) {
            flying.remove(player.getUuid());
        }
        player.setAllowFlying(enable);
        player.setFlying(enable);
        if (!enable) {
            allowDoubleJump(player);
        }
        return enable;
    }

    /** True if the player has {@code /fly} on. */
    public boolean isFlying(Player player) {
        return flying.contains(player.getUuid());
    }

    private boolean ownFlight(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || flying.contains(player.getUuid());
    }

    private boolean mayDoubleJump(Player player) {
        MovementConfig.DoubleJump doubleJump = settings().doubleJump();
        return doubleJump.enabled() && !ownFlight(player)
                && (doubleJump.permission().isEmpty() || permissions.hasPermission(player, doubleJump.permission()));
    }

    private void allowDoubleJump(Player player) {
        if (ownFlight(player)) {
            return;
        }
        player.setAllowFlying(mayDoubleJump(player));
    }

    private void doubleJump(Player player) {
        if (ownFlight(player)) {
            return;
        }
        // Whatever happens, flying itself is not allowed here.
        player.setFlying(false);
        player.setAllowFlying(false);
        if (!mayDoubleJump(player)) {
            return;
        }
        MovementConfig.DoubleJump doubleJump = settings().doubleJump();
        long now = System.currentTimeMillis();
        Long last = lastDoubleJump.get(player.getUuid());
        if (last != null && now - last < doubleJump.cooldownMillis()) {
            return;
        }
        lastDoubleJump.put(player.getUuid(), now);
        throwPlayer(player, horizontalLook(player).mul(doubleJump.forward()).withY(doubleJump.up()), doubleJump.effect());
    }

    private void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Pos to = event.getNewPosition();
        if (!player.isAllowFlying() && event.isOnGround() && mayDoubleJump(player)) {
            // Landed: the next double jump may come once the cooldown is over.
            Long last = lastDoubleJump.get(player.getUuid());
            if (last == null || System.currentTimeMillis() - last >= settings().doubleJump().cooldownMillis()) {
                player.setAllowFlying(true);
            }
        }
        if (!settings().jumpPadsEnabled() || sameBlock(player.getPosition(), to)) {
            return;
        }
        Instance instance = player.getInstance();
        if (instance == null) {
            return;
        }
        Block feet = instance.getBlock(to);
        if (feet.compare(Block.AIR)) {
            return;
        }
        Block under = instance.getBlock(to.sub(0, 1, 0));
        for (MovementConfig.JumpPad pad : settings().jumpPads()) {
            if (pad.matches(feet, under) && padReady(player)) {
                throwPlayer(player, horizontalLook(player).mul(pad.forward()).withY(pad.up()), pad.effect());
                return;
            }
        }
    }

    @Override
    public void entered(Player player, Region region) {
        for (MovementConfig.LaunchPad pad : settings().launchPads()) {
            if (pad.region().name().equals(region.name()) && padReady(player)) {
                throwPlayer(player, pad.velocity(), pad.effect());
                return;
            }
        }
    }

    private boolean padReady(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastPad.get(player.getUuid());
        if (last != null && now - last < PAD_COOLDOWN_MILLIS) {
            return false;
        }
        lastPad.put(player.getUuid(), now);
        return true;
    }

    private static void throwPlayer(Player player, Vec velocity, MovementConfig.Effect effect) {
        player.setVelocity(velocity);
        Pos at = player.getPosition();
        if (effect.sound() != null && player.getInstance() != null) {
            player.getInstance().playSound(Sound.sound(effect.sound(), Sound.Source.PLAYER, 1f, 1f), at.x(), at.y(), at.z());
        }
        if (effect.particle() != null) {
            player.sendPacketToViewersAndSelf(new ParticlePacket(effect.particle(), at, PARTICLE_SPREAD, PARTICLE_SPEED,
                    PARTICLE_COUNT));
        }
    }

    /** The direction the player looks in, flattened, one block long. */
    static Vec horizontalLook(Player player) {
        Vec look = player.getPosition().direction();
        Vec flat = new Vec(look.x(), 0, look.z());
        return flat.isZero() ? Vec.ZERO : flat.normalize();
    }

    private static boolean sameBlock(Pos a, Pos b) {
        return a.blockX() == b.blockX() && a.blockY() == b.blockY() && a.blockZ() == b.blockZ();
    }

    /** Pads in the file, for /lobby info and tests. */
    public List<MovementConfig.LaunchPad> launchPads() {
        return settings().launchPads();
    }
}
