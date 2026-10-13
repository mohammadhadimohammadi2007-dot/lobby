package io.github.mohammadhadimohammadi2007_dot.lobby.server.pvp;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.CombatTagEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.PvpAreaEnterEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.PvpAreaLeaveEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.PvpDamageEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.PvpKillEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.action.ActionServices;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.LobbyEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.data.YamlDataStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.Region;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionListener;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.region.RegionTracker;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerCommandEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The PvP area: players who walk in get the kit and can fight each other; leaving gives the lobby's items
 * back. Hits follow the attacker's combat mode ({@link CombatMode}); a hit tags both players for a while
 * ({@link CombatTags}), during which they cannot leave, switch servers or lobbies.
 *
 * <p>Nobody dies: a hit that would kill sends the player back to the area's spawn with a new kit and counts
 * the kill. Everything runs on the tick thread, from the events of the players.
 */
public final class PvpService implements RegionListener {

    private static final String FILE_NAME = "pvp.yml";
    private static final String HEADER = """
            The PvP area, normally set in game with /pvp. See docs/pvp.md.

            from and to are the corners of the area, spawn is where players come back after dying, and kit the
            items they get (slot number or armor piece, then the item in Minecraft's SNBT format).""";
    private static final long MILLIS_PER_TICK = 50;
    /** Shortest time between two "you cannot do that" messages to one player. */
    private static final long NOTICE_MILLIS = 1500;
    /** How far around the hit player a sweep reaches (blocks, horizontally). */
    private static final double SWEEP_REACH = 1.5;
    /** A combat tag that grew by more than this since the proxy was told is sent to the proxy again. */
    private static final long BRIDGE_RESEND_MILLIS = 2000;
    private static final double MAX_HEALTH = 20;

    /** What the rest of the lobby does for the PvP area. */
    public record Hooks(Consumer<Player> giveLobbyItems, Consumer<Player> stopFlying, Consumer<Player> refreshVisibility) {
    }

    private final ConfigManager config;
    private final LobbyText text;
    private final BridgeService bridge;
    private final RegionTracker regions;
    private final YamlDataStore<PvpArena> store;
    private final PvpStatsService stats;
    private final ActionServices actions;
    private final LongSupplier clock;
    private final CombatTags tags = new CombatTags();
    private final Set<UUID> inArea = ConcurrentHashMap.newKeySet();
    private final Map<UUID, HurtTimer> hurt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSwing = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> falling = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Map<UUID, Long> notices = new ConcurrentHashMap<>();
    private final Map<UUID, Long> toldProxyUntil = new ConcurrentHashMap<>();
    private volatile Hooks hooks = new Hooks(player -> { }, player -> { }, player -> { });
    private volatile @Nullable PvpArena arena;
    private @Nullable Task ticker;

    private PvpService(ConfigManager config, LobbyText text, BridgeService bridge, RegionTracker regions,
                       YamlDataStore<PvpArena> store, PvpStatsService stats, ActionServices actions, LongSupplier clock) {
        this.config = config;
        this.text = text;
        this.bridge = bridge;
        this.regions = regions;
        this.store = store;
        this.stats = stats;
        this.actions = actions;
        this.clock = clock;
    }

    /** Reads data/pvp.yml and starts following the area. */
    public static PvpService start(Path dataDir, ConfigManager config, LobbyText text, BridgeService bridge,
                                   RegionTracker regions, PvpStatsService stats, ActionServices actions,
                                   LongSupplier clock) {
        YamlDataStore<PvpArena> store = new YamlDataStore<>(dataDir.resolve("data").resolve(FILE_NAME), HEADER,
                new PvpArenaCodec());
        PvpService service = new PvpService(config, text, bridge, regions, store, stats, actions, clock);
        regions.listen(PvpArena.OWNER, service);
        service.reload();
        return service;
    }

    public void hooks(Hooks value) {
        hooks = value;
    }

    // ---------------- State ----------------

    public PvpConfig config() {
        return config.current().pvp();
    }

    public PvpStatsService stats() {
        return stats;
    }

    public @Nullable PvpArena arena() {
        return arena;
    }

    /** True if the area is set up and pvp.yml has it enabled. */
    public boolean available() {
        return arena != null && config().enabled();
    }

    public boolean inArea(Player player) {
        return inArea.contains(player.getUuid());
    }

    public int playersInArea() {
        return inArea.size();
    }

    /** Seconds of combat left. */
    public long combatSecondsLeft(Player player) {
        return tags.remainingSeconds(player.getUuid(), clock.getAsLong());
    }

    public boolean inCombat(Player player) {
        return tags.tagged(player.getUuid(), clock.getAsLong());
    }

    /**
     * True (and the player is told why) if the player may not switch servers or lobbies now. For the connector
     * and the lobby switcher.
     */
    public boolean blocksSwitching(Player player) {
        if (!inCombat(player)) {
            return false;
        }
        notice(player, MessageKey.PVP_IN_COMBAT);
        return true;
    }

    /** Problems of data/pvp.yml, for the doctor. */
    public List<String> brokenEntries() {
        return List.copyOf(store.brokenEntries());
    }

    // ---------------- Setup (/pvp) ----------------

    /** Reads data/pvp.yml again (/setup-lobby reload). Tick thread. */
    public synchronized void reload() {
        arena = store.load().get(PvpArena.NAME);
        applyArena();
    }

    /** Makes {@code region} the PvP area, keeping the spawn if it is still inside. */
    public synchronized void setRegion(Region region) {
        Region named = new Region(PvpArena.OWNER, PvpArena.NAME, region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ());
        PvpArena current = arena;
        Pos spawn = current == null || current.spawn() == null || !contains(named, current.spawn()) ? null : current.spawn();
        arena = new PvpArena(named, spawn, current == null ? Kit.EMPTY : current.kit());
        save();
    }

    /** False if there is no area or the position is outside it. */
    public synchronized boolean setSpawn(Pos position) {
        PvpArena current = arena;
        if (current == null || !contains(current.region(), position)) {
            return false;
        }
        arena = current.withSpawn(position);
        save();
        return true;
    }

    /** False if there is no area. */
    public synchronized boolean setKit(Kit kit) {
        PvpArena current = arena;
        if (current == null) {
            return false;
        }
        arena = current.withKit(kit);
        save();
        return true;
    }

    /** Removes the area; everyone in it gets their lobby items back. False if there was none. */
    public synchronized boolean delete() {
        if (arena == null) {
            return false;
        }
        arena = null;
        save();
        return true;
    }

    private void save() {
        PvpArena current = arena;
        store.save(current == null ? Map.of() : Map.of(PvpArena.NAME, current));
        applyArena();
    }

    /** Tells the region tracker where the area is, and sends out who is no longer in it. */
    private void applyArena() {
        PvpArena current = arena;
        regions.setRegions(PvpArena.OWNER, current == null ? List.of() : List.of(current.region()));
        for (UUID id : Set.copyOf(inArea)) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (player != null && (current == null || !contains(current.region(), player.getPosition()))) {
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> leave(player));
            }
        }
    }

    private static boolean contains(Region region, Point point) {
        return region.contains(point.blockX(), point.blockY(), point.blockZ());
    }

    // ---------------- Listeners ----------------

    /** Starts following attacks, movement, commands and quits. */
    public void register(EventNode<Event> global, EventNode<PlayerEvent> players) {
        global.addListener(EntityAttackEvent.class, this::onAttack);
        global.addListener(ItemDropEvent.class, event -> {
            if (event.getEntity() instanceof Player player && inArea(player)) {
                event.setCancelled(true);
            }
        });
        players.addListener(PlayerMoveEvent.class, this::onMove);
        players.addListener(PlayerHandAnimationEvent.class, event -> lastSwing.put(event.getPlayer().getUuid(),
                clock.getAsLong()));
        players.addListener(PlayerChangeHeldSlotEvent.class, event -> lastSwing.put(event.getPlayer().getUuid(),
                clock.getAsLong()));
        players.addListener(PlayerCommandEvent.class, this::onCommand);
        players.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();
            if (event.isFirstSpawn()) {
                stats.load(player);
            } else if (inArea(player)) {
                // Moved to another lobby instance: it has its own area, which the player is not in yet.
                leave(player);
            }
        });
        players.addListener(PlayerDisconnectEvent.class, event -> onQuit(event.getPlayer()));
        ticker = MinecraftServer.getSchedulerManager().buildTask(this::tick)
                .repeat(TaskSchedule.seconds(1))
                .schedule();
    }

    /** Players are told every second how long their combat lasts; ended tags are announced. */
    void tick() {
        long now = clock.getAsLong();
        for (UUID id : tags.expire(now)) {
            toldProxyUntil.remove(id);
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (player != null) {
                player.sendActionBar(text.message(MessageKey.PVP_COMBAT_ENDED, player));
                fire(new CombatTagEvent(player, false));
            }
        }
        for (UUID id : tags.tagged()) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
            if (player != null) {
                player.sendActionBar(text.message(MessageKey.PVP_COMBAT_TAGGED, player,
                        Messages.text("seconds", tags.remainingSeconds(id, now))));
            }
        }
    }

    @Override
    public void entered(Player player, Region region) {
        if (!available() || !inArea.add(player.getUuid())) {
            return;
        }
        PvpArena current = arena;
        hooks.stopFlying().accept(player);
        current.kit().apply(player);
        heal(player);
        hurt.computeIfAbsent(player.getUuid(), ignored -> new HurtTimer()).reset();
        hooks.refreshVisibility().accept(player);
        player.sendMessage(text.message(MessageKey.PVP_ENTERED, player));
        fire(new PvpAreaEnterEvent(player));
    }

    @Override
    public void left(Player player, Region region) {
        // In combat the move was cancelled before the tracker saw it, so this is a real exit.
        leave(player);
    }

    /** Takes the player out of the area: kit gone, lobby items back. */
    void leave(Player player) {
        if (!inArea.remove(player.getUuid())) {
            return;
        }
        endCombat(player, false);
        player.getInventory().clear();
        heal(player);
        hooks.giveLobbyItems().accept(player);
        hooks.refreshVisibility().accept(player);
        player.sendMessage(text.message(MessageKey.PVP_LEFT, player));
        fire(new PvpAreaLeaveEvent(player));
    }

    private void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Pos from = player.getPosition();
        Pos to = event.getNewPosition();
        falling.put(player.getUuid(), !event.isOnGround() && to.y() < from.y());
        PvpArena current = arena;
        if (current == null || !inArea(player) || !inCombat(player)) {
            return;
        }
        if (contains(current.region(), from) && !contains(current.region(), to)) {
            // In combat nobody leaves: the move is undone (the region tracker skips cancelled moves).
            event.setCancelled(true);
            notice(player, MessageKey.PVP_IN_COMBAT);
        }
    }

    private void onCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled() || !inArea(player)) {
            return;
        }
        String root = event.getCommand().strip().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        PvpConfig settings = config();
        if (settings.areaBlockedCommands().contains(root)) {
            event.setCancelled(true);
            player.sendMessage(text.message(MessageKey.PVP_COMMAND_BLOCKED, player));
        } else if (settings.combatBlockedCommands().contains(root) && inCombat(player)) {
            event.setCancelled(true);
            notice(player, MessageKey.PVP_IN_COMBAT);
        }
    }

    private void onQuit(Player player) {
        UUID id = player.getUuid();
        long now = clock.getAsLong();
        if (inArea.remove(id) && tags.tagged(id, now) && config().combatLogCountsAsDeath()) {
            Player killer = online(lastAttacker.get(id));
            stats.update(id, player.getUsername(), record -> record.withDeath(player.getUsername()));
            if (killer != null) {
                stats.update(killer.getUuid(), killer.getUsername(), record -> record.withKill(killer.getUsername()));
            }
            tellArea(text.message(MessageKey.PVP_COMBAT_LOG, (Player) null, Messages.text("victim", player.getUsername())));
            fire(new PvpKillEvent(player, killer, true));
        }
        tags.clear(id, now);
        hurt.remove(id);
        lastSwing.remove(id);
        falling.remove(id);
        lastAttacker.remove(id);
        lastAttacker.values().removeIf(id::equals);
        notices.remove(id);
        toldProxyUntil.remove(id);
        stats.forget(id);
    }

    // ---------------- Fighting ----------------

    private void onAttack(EntityAttackEvent event) {
        if (!(event.getEntity() instanceof Player attacker) || !(event.getTarget() instanceof Player victim)) {
            return;
        }
        PvpConfig settings = config();
        if (!available() || !inArea(attacker) || !inArea(victim) || attacker.getInstance() != victim.getInstance()
                || victim.isDead() || !fighter(attacker) || !fighter(victim)
                || attacker.getPosition().distance(victim.getPosition()) > settings.maxReach()) {
            return;
        }
        CombatMode mode = CombatMode.of(bridge.capabilities(attacker));
        if (settings.sameVersionOnly() && mode != CombatMode.of(bridge.capabilities(victim))) {
            if (passNotice(attacker)) {
                attacker.sendActionBar(text.message(MessageKey.PVP_SAME_VERSION_ONLY, attacker));
            }
            return;
        }
        long now = clock.getAsLong();
        Weapon weapon = Weapon.of(attacker.getItemInMainHand(), mode);
        double charge = 1;
        if (mode == CombatMode.MODERN) {
            Long last = lastSwing.get(attacker.getUuid());
            long ticks = last == null ? Long.MAX_VALUE / 2 : (now - last) / MILLIS_PER_TICK;
            charge = CombatMath.attackStrength(ticks, weapon.attackSpeed());
        }
        lastSwing.put(attacker.getUuid(), now);
        CombatMath.Swing swing = new CombatMath.Swing(mode, weapon, charge,
                falling.getOrDefault(attacker.getUuid(), false), attacker.isSprinting(), attacker.isOnGround());
        CombatMath.Hit hit = CombatMath.hit(swing, Armor.of(victim));
        boolean landed = hurt(attacker, victim, hit.damage(), hit.critical(), mode, hit.extraKnockback());
        if (landed && hit.sweep() && settings.sweepAttacks()) {
            sweep(attacker, victim, weapon, hit.damage(), mode, settings);
        }
    }

    /** Only players in survival or adventure fight; a creative admin walking through is left alone. */
    private static boolean fighter(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void sweep(Player attacker, Player victim, Weapon weapon, double mainDamage, CombatMode mode,
                       PvpConfig settings) {
        double damage = CombatMath.sweepDamage(weapon, mainDamage);
        for (Player other : victim.getInstance().getPlayers()) {
            if (other == attacker || other == victim || !inArea(other) || !fighter(other)
                    || Math.abs(other.getPosition().y() - victim.getPosition().y()) > 1
                    || horizontalDistance(other.getPosition(), victim.getPosition()) > SWEEP_REACH
                    || settings.sameVersionOnly() && mode != CombatMode.of(bridge.capabilities(other))) {
                continue;
            }
            hurt(attacker, other, CombatMath.reduce(mode, damage, Armor.of(other)), false, mode, 0);
        }
        victim.getInstance().sendGroupedPacket(new ParticlePacket(Particle.SWEEP_ATTACK,
                victim.getPosition().add(0, 1, 0), Pos.ZERO, 0, 1));
    }

    private static double horizontalDistance(Point a, Point b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * One hit after the attacker's mode worked out its damage: addons may change it, the hurt timer may
     * shorten it, then the victim loses health and is knocked back (or respawns if it was the last hit).
     *
     * @return true if the hit did anything
     */
    private boolean hurt(Player attacker, Player victim, double damage, boolean critical, CombatMode mode,
                         int extraKnockback) {
        if (LobbyEvents.active()) {
            PvpDamageEvent event = LobbyEvents.fire(new PvpDamageEvent(victim, attacker, damage, critical,
                    mode == CombatMode.LEGACY));
            if (event.isCancelled()) {
                return false;
            }
            damage = event.damage();
        }
        long now = clock.getAsLong();
        HurtTimer.Result result = hurt.computeIfAbsent(victim.getUuid(), ignored -> new HurtTimer()).hit(now, damage);
        if (result.damage() <= 0) {
            return false;
        }
        tag(attacker, now);
        tag(victim, now);
        lastAttacker.put(victim.getUuid(), attacker.getUuid());
        if (victim.getHealth() - result.damage() <= 0) {
            kill(victim, attacker);
            return true;
        }
        victim.damage(Damage.fromPlayer(attacker, (float) result.damage()));
        if (critical) {
            victim.sendPacketToViewersAndSelf(new EntityAnimationPacket(victim.getEntityId(),
                    EntityAnimationPacket.Animation.CRITICAL_EFFECT));
        }
        if (result.knockback()) {
            knockback(attacker, victim, mode, extraKnockback);
        }
        return true;
    }

    private static void knockback(Player attacker, Player victim, CombatMode mode, int extra) {
        Pos from = attacker.getPosition();
        Pos to = victim.getPosition();
        if (mode == CombatMode.LEGACY) {
            victim.setVelocity(CombatMath.legacyKnockback(to.x() - from.x(), to.z() - from.z(), from.yaw(), extra));
            return;
        }
        // Minestom's knockback is modern Minecraft's: away from the source, then the attacker's look direction.
        victim.takeKnockback((float) CombatMath.BASE_KNOCKBACK, from.x() - to.x(), from.z() - to.z());
        if (extra > 0) {
            double yaw = Math.toRadians(from.yaw());
            victim.takeKnockback((float) (extra * CombatMath.EXTRA_KNOCKBACK), Math.sin(yaw), -Math.cos(yaw));
        }
    }

    /** The last hit: counted, announced, and the victim is back at the spawn with a new kit. */
    void kill(Player victim, @Nullable Player killer) {
        PvpArena current = arena;
        stats.update(victim.getUuid(), victim.getUsername(), record -> record.withDeath(victim.getUsername()));
        PvpStats killerStats = killer == null ? null : stats.update(killer.getUuid(), killer.getUsername(),
                record -> record.withKill(killer.getUsername()));
        endCombat(victim, true);
        lastAttacker.remove(victim.getUuid());
        HurtTimer timer = hurt.get(victim.getUuid());
        if (timer != null) {
            timer.reset();
        }
        heal(victim);
        if (current != null) {
            current.kit().apply(victim);
            victim.teleport(current.respawn());
        }
        if (killer != null) {
            tellArea(text.message(MessageKey.PVP_KILL, (Player) null, Messages.text("killer", killer.getUsername()),
                    Messages.text("victim", victim.getUsername()), Messages.text("streak", killerStats.streak())));
            config().killActions().run(killer, actions, "pvp.yml kill-actions");
        }
        config().deathActions().run(victim, actions, "pvp.yml death-actions");
        fire(new PvpKillEvent(victim, killer, false));
    }

    private static void heal(Player player) {
        player.setHealth((float) Math.max(1, player.getAttributeValue(Attribute.MAX_HEALTH) > 0
                ? player.getAttributeValue(Attribute.MAX_HEALTH) : MAX_HEALTH));
        player.setFood(20);
        player.setFoodSaturation(5);
    }

    // ---------------- Combat tag ----------------

    private void tag(Player player, long now) {
        long duration = config().combatTagMillis();
        if (duration <= 0) {
            return;
        }
        UUID id = player.getUuid();
        if (tags.tag(id, now, duration)) {
            fire(new CombatTagEvent(player, true));
            player.sendActionBar(text.message(MessageKey.PVP_COMBAT_TAGGED, player,
                    Messages.text("seconds", (duration + 999) / 1000)));
        }
        long until = now + duration;
        Long told = toldProxyUntil.get(id);
        if (told == null || until - told > BRIDGE_RESEND_MILLIS) {
            toldProxyUntil.put(id, until);
            bridge.sendCombatTag(player, duration,
                    config.current().messages().template(MessageKey.PVP_PROXY_BLOCKED));
        }
    }

    /** Ends a player's combat at once (death, leaving), also on the proxy. */
    private void endCombat(Player player, boolean announce) {
        UUID id = player.getUuid();
        if (tags.clear(id, clock.getAsLong())) {
            if (announce) {
                player.sendActionBar(Component.empty());
            }
            fire(new CombatTagEvent(player, false));
        }
        if (toldProxyUntil.remove(id) != null) {
            bridge.sendCombatTag(player, 0, "");
        }
    }

    // ---------------- Helpers ----------------

    private void notice(Player player, MessageKey key) {
        if (passNotice(player)) {
            player.sendMessage(text.message(key, player,
                    Messages.text("seconds", tags.remainingSeconds(player.getUuid(), clock.getAsLong()))));
        }
    }

    private boolean passNotice(Player player) {
        long now = clock.getAsLong();
        Long last = notices.get(player.getUuid());
        if (last != null && now - last < NOTICE_MILLIS) {
            return false;
        }
        notices.put(player.getUuid(), now);
        return true;
    }

    private void tellArea(Component message) {
        List<Player> receivers = new ArrayList<>();
        for (UUID id : inArea) {
            Player player = online(id);
            if (player != null) {
                receivers.add(player);
            }
        }
        receivers.forEach(player -> player.sendMessage(message));
    }

    private static @Nullable Player online(@Nullable UUID id) {
        return id == null ? null : MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(id);
    }

    private static void fire(Event event) {
        if (LobbyEvents.active()) {
            LobbyEvents.fire(event);
        }
    }

    /** Players in the area who also see each other there, for the visibility rules. */
    public Predicate<Player> inAreaPredicate() {
        return this::inArea;
    }

    /** Stops the ticker and writes data/pvp.yml. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        store.close();
    }
}
