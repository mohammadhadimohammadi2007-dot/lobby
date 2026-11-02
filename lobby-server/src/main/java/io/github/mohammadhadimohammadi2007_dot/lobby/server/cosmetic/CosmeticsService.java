package io.github.mohammadhadimohammadi2007_dot.lobby.server.cosmetic;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.CosmeticChangeEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.GadgetUseEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Cosmetics;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.LobbyEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.Messages;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.MeasuredThread;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Trails, hats, gadgets, pets, balloons and outfits. Players pick them in the cosmetics menu; their choice is saved
 * with their other settings. A cosmetic shows only while the player may use it (its permission) and their client can
 * show it ({@code min-version}); a choice that stops qualifying is kept but not shown.
 *
 * <p>Everything a viewer sees of someone's cosmetics, except the hat and outfit (real items worn), is particles and
 * packet-only entities sent from the {@code lobby-effects} thread: never from the tick thread. Only players within
 * {@code view-distance} who can see the player (player visibility) and do not hide other players' cosmetics get
 * them, and a {@link ParticleBudget} caps what one player receives and what the server sends per tick (pets and
 * balloons: see {@link Followers}). Cosmetics are off for players in the PvP area (and anywhere else a
 * {@link #suppressWhen} rule says so).
 */
public final class CosmeticsService {

    /** Ticks between two checks of a player's trail permission and client version. */
    private static final int RECHECK_TICKS = 20;
    /** A player moved this far (squared, blocks) since the last tick counts as walking, for feet trails. */
    private static final double MOVING_SQUARED = 0.0025;
    private static final double LEAP_FORWARD = 12;
    private static final double LEAP_UP = 8;
    /** A grapple pulls at this speed plus some per block of distance, in blocks per second, before its power. */
    private static final double GRAPPLE_BASE = 8;
    private static final double GRAPPLE_PER_BLOCK = 1.2;
    private static final double GRAPPLE_MAX = 40;
    private static final double GRAPPLE_UP = 4;
    /** How far in front of the player a TNT lands, and how long it blinks before it explodes. */
    private static final double TNT_AHEAD = 1.5;
    static final int TNT_FUSE_TICKS = 40;
    /** Players this close to an exploding TNT are pushed away. */
    private static final double TNT_REACH = 5;
    private static final double TNT_PUSH = 14;
    /** How long a lightning bolt stays before it is removed. */
    private static final int LIGHTNING_TICKS = 20;

    /** What the service needs from the rest of the lobby. */
    public record Dependencies(ConfigManager config, LobbyText text, PermissionService permissions,
                               ChatSettingsService settings, ToIntFunction<Player> protocol,
                               BiPredicate<Player, Player> canSee, MeasuredThread effects, LongSupplier clock) {
    }

    /** The outcome of {@link #equip} and {@link #unequip}. */
    public enum Change { DONE, UNKNOWN, LOCKED, UNSUPPORTED, CANCELLED, DISABLED }

    /** A player's decoded choices, kept until the saved text changes. */
    private record Cached(@Nullable String raw, CosmeticChoices choices) {
    }

    /** What the effects thread knows about one player's trail, pet and balloon. Effects thread only. */
    private static final class TrailState {
        @Nullable Cosmetic trail;
        @Nullable Cosmetic pet;
        @Nullable Cosmetic balloon;
        @Nullable String raw;
        long checkedTick = Long.MIN_VALUE;
        @Nullable Pos last;
    }

    /**
     * Where a gadget is used: the player's feet and eyes, where they look, and for gadgets that aim, the block they
     * aim at (or {@code null}) and, for a paintball, its paint.
     */
    private record Use(Pos at, Pos eye, Vec look, CosmeticEffects.@Nullable Hit hit,
                       CosmeticEffects.@Nullable Paint paint) {
    }

    /** A trail shown to one player only, from the menu, until {@code untilMillis}. */
    private record Preview(Cosmetic cosmetic, long untilMillis) {
    }

    private final Dependencies deps;
    private final Map<UUID, Cached> choices = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> gadgetUses = new ConcurrentHashMap<>();
    private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();
    private final List<Predicate<Player>> suppressors = new CopyOnWriteArrayList<>();
    private volatile Consumer<Player> itemsChanged = player -> { };
    // Effects thread only.
    private final Map<UUID, TrailState> trails = new HashMap<>();
    private final ParticleBudget<Player> budget = new ParticleBudget<>();
    private final Followers followers;
    private final java.util.Random random = new java.util.Random();
    private long tick;
    private volatile boolean configChanged;
    private final AtomicBoolean ticking = new AtomicBoolean();
    private @Nullable Task task;

    public CosmeticsService(Dependencies deps) {
        this.deps = deps;
        this.followers = new Followers(new Followers.Hooks() {
            @Override
            public List<Player> viewers(Player owner, Pos at, @Nullable Instance instance) {
                return CosmeticsService.this.viewers(owner, at, instance);
            }

            @Override
            public Component nameTag(String text, Player owner) {
                return deps.text().render(text, owner);
            }
        });
    }

    public CosmeticsConfig config() {
        return deps.config().current().cosmetics();
    }

    /** messages.yml, for the menu's texts. */
    public Messages messages() {
        return deps.config().current().messages();
    }

    /** Players for whom cosmetics are off right now (fighters in the PvP area...). Rules add up. */
    public void suppressWhen(Predicate<Player> rule) {
        suppressors.add(rule);
    }

    /** Run when a player's hat or gadget changed, to give them their items again. */
    public void onItemsChanged(Consumer<Player> listener) {
        itemsChanged = listener;
    }

    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerDisconnectEvent.class, event -> forget(event.getPlayer().getUuid()));
        task = MinecraftServer.getSchedulerManager().buildTask(() -> {
            // One tick of effects at a time: if the thread is behind, this tick is skipped, not queued.
            if (ticking.compareAndSet(false, true)) {
                deps.effects().execute(() -> {
                    try {
                        tick();
                    } finally {
                        ticking.set(false);
                    }
                });
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }

    /** After {@code /setup-lobby reload}: cosmetics may have changed or gone. */
    public void reload() {
        configChanged = true;
    }

    private void forget(UUID player) {
        choices.remove(player);
        gadgetUses.remove(player);
        previews.remove(player);
        deps.effects().execute(() -> {
            trails.remove(player);
            followers.forget(player);
        });
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
    }

    // ---------------- Choices ----------------

    /** What the player chose (saved), whether or not they may use it now. */
    public CosmeticChoices choices(Player player) {
        String raw = deps.settings().get(player.getUuid()).cosmetics();
        Cached cached = choices.get(player.getUuid());
        if (cached != null && java.util.Objects.equals(cached.raw(), raw)) {
            return cached.choices();
        }
        CosmeticChoices decoded = CosmeticChoices.decode(raw);
        choices.put(player.getUuid(), new Cached(raw, decoded));
        return decoded;
    }

    /** True if the player has the cosmetic's permission, or it needs none. */
    public boolean unlocked(Player player, Cosmetic cosmetic) {
        return cosmetic.permission().isEmpty() || deps.permissions().hasPermission(player, cosmetic.permission());
    }

    /** True if the player's client can show the cosmetic. */
    public boolean supported(Player player, Cosmetic cosmetic) {
        return cosmetic.minProtocol() == 0 || deps.protocol().applyAsInt(player) >= cosmetic.minProtocol();
    }

    /** The cosmetic of this type the player uses and may use now, or {@code null}. */
    public @Nullable Cosmetic equipped(Player player, CosmeticType type) {
        CosmeticsConfig config = config();
        if (!config.enabled()) {
            return null;
        }
        Cosmetic cosmetic = config.find(type, choices(player).get(type));
        return cosmetic != null && unlocked(player, cosmetic) && supported(player, cosmetic) ? cosmetic : null;
    }

    /** True if cosmetics are off for this player right now (in the PvP area, spectating...). */
    public boolean suppressed(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        for (Predicate<Player> rule : suppressors) {
            if (rule.test(player)) {
                return true;
            }
        }
        return false;
    }

    /** Puts on {@code cosmetic} (taking off another of its type) and saves it. Tick thread. */
    public Change equip(Player player, Cosmetic cosmetic) {
        if (!config().enabled()) {
            return Change.DISABLED;
        }
        if (!unlocked(player, cosmetic)) {
            return Change.LOCKED;
        }
        if (!supported(player, cosmetic)) {
            return Change.UNSUPPORTED;
        }
        return change(player, cosmetic.type(), cosmetic.id()) ? Change.DONE : Change.CANCELLED;
    }

    /** Takes off the player's cosmetic of this type. Tick thread. */
    public Change unequip(Player player, CosmeticType type) {
        if (choices(player).get(type) == null) {
            return Change.DONE;
        }
        return change(player, type, null) ? Change.DONE : Change.CANCELLED;
    }

    /** Takes off everything. */
    public void clear(Player player) {
        for (CosmeticType type : CosmeticType.values()) {
            unequip(player, type);
        }
    }

    private boolean change(Player player, CosmeticType type, @Nullable String id) {
        CosmeticChoices current = choices(player);
        String previous = current.get(type);
        if (java.util.Objects.equals(previous, id)) {
            return true;
        }
        if (LobbyEvents.active() && LobbyEvents.fire(new CosmeticChangeEvent(player, kind(type), previous, id)).isCancelled()) {
            return false;
        }
        save(player, current.with(type, id));
        if (type == CosmeticType.HAT || type == CosmeticType.GADGET || type == CosmeticType.OUTFIT) {
            itemsChanged.accept(player);
        }
        return true;
    }

    /** Switches whether the player sees other players' trails and gadget effects. Returns the new value. */
    public boolean toggleHideOthers(Player player) {
        CosmeticChoices updated = choices(player).withHideOthers(!choices(player).hideOthers());
        save(player, updated);
        return updated.hideOthers();
    }

    private void save(Player player, CosmeticChoices updated) {
        String raw = updated.encode();
        deps.settings().update(player.getUuid(), settings -> settings.withCosmetics(raw));
        choices.put(player.getUuid(), new Cached(raw, updated));
    }

    /** How many cosmetics of this type the player has unlocked, of those their client can show. */
    public int unlockedCount(Player player, CosmeticType type) {
        return (int) config().of(type).stream()
                .filter(cosmetic -> supported(player, cosmetic) && unlocked(player, cosmetic)).count();
    }

    /** The cosmetic's name for this player, from its MiniMessage. */
    public Component name(Cosmetic cosmetic, @Nullable Player player) {
        return deps.text().render(cosmetic.name(), player);
    }

    static Cosmetics.Kind kind(CosmeticType type) {
        return Cosmetics.Kind.valueOf(type.name());
    }

    // ---------------- Hat and gadget items ----------------

    /** What the player wears on the head, or {@code null}. Any thread. */
    public @Nullable net.minestom.server.item.ItemStack hat(Player player) {
        Cosmetic hat = equipped(player, CosmeticType.HAT);
        return hat == null || hat.hat() == null ? null : hat.hat().item();
    }

    /** The gadget item for the hotbar, or {@code null}. Any thread (it renders text). */
    public net.minestom.server.item.@Nullable ItemStack gadgetItem(Player player, boolean legacyClient) {
        Cosmetic gadget = equipped(player, CosmeticType.GADGET);
        if (gadget == null || gadget.gadget() == null) {
            return null;
        }
        List<String> lore = new ArrayList<>(gadget.lore());
        lore.add(deps.config().current().messages().template(MessageKey.COSMETICS_GADGET_USE)
                .replace("<seconds>", String.valueOf(gadget.gadget().cooldownMillis() / 1000)));
        return io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuItems.build(gadget.iconItem(),
                gadget.name(), lore, false, player, deps.text(), legacyClient);
    }

    /**
     * The outfit's pieces by slot, empty for none. Its helmet only when the player wears no hat (the hat wins).
     * Any thread.
     */
    public Map<EquipmentSlot, net.minestom.server.item.ItemStack> outfit(Player player) {
        Cosmetic outfit = equipped(player, CosmeticType.OUTFIT);
        if (outfit == null || outfit.outfit() == null) {
            return Map.of();
        }
        Map<EquipmentSlot, net.minestom.server.item.ItemStack> items = outfit.outfit().items();
        if (equipped(player, CosmeticType.HAT) != null) {
            items.remove(EquipmentSlot.HELMET);
        }
        return items;
    }

    /** The hat, outfit and gadget item for the hotbar, which puts them on with the other items. */
    public HotbarService.Extras hotbarExtras() {
        return new HotbarService.Extras() {
            @Override
            public net.minestom.server.item.@Nullable ItemStack hat(Player player) {
                return CosmeticsService.this.hat(player);
            }

            @Override
            public Map<EquipmentSlot, net.minestom.server.item.ItemStack> outfit(Player player) {
                return CosmeticsService.this.outfit(player);
            }

            @Override
            public net.minestom.server.item.@Nullable ItemStack gadget(Player player, boolean legacyClient) {
                return gadgetItem(player, legacyClient);
            }

            @Override
            public void useGadget(Player player) {
                CosmeticsService.this.useGadget(player);
            }
        };
    }

    /** Right-click with the gadget item: uses the gadget unless it is on cooldown. Tick thread. */
    public void useGadget(Player player) {
        Cosmetic cosmetic = equipped(player, CosmeticType.GADGET);
        if (cosmetic == null || cosmetic.gadget() == null) {
            return;
        }
        if (suppressed(player)) {
            player.sendMessage(deps.text().message(MessageKey.COSMETICS_NOT_HERE, player));
            return;
        }
        Cosmetic.Gadget gadget = cosmetic.gadget();
        long now = deps.clock().getAsLong();
        Map<String, Long> uses = gadgetUses.computeIfAbsent(player.getUuid(), id -> new ConcurrentHashMap<>());
        Long last = uses.get(cosmetic.id());
        if (last != null && now - last < gadget.cooldownMillis()) {
            long seconds = Math.max(1, (gadget.cooldownMillis() - (now - last) + 999) / 1000);
            player.sendActionBar(deps.text().message(MessageKey.COSMETICS_GADGET_COOLDOWN, player,
                    Placeholder.component("name", name(cosmetic, player)), Messages.text("seconds", seconds)));
            return;
        }
        Use use = aim(player, gadget);
        if (gadget.type().needsTarget() && use.hit() == null) {
            // Missing costs nothing: no cooldown.
            player.sendActionBar(deps.text().message(MessageKey.COSMETICS_GADGET_NO_TARGET, player,
                    Messages.text("blocks", gadget.range())));
            return;
        }
        if (LobbyEvents.active() && LobbyEvents.fire(new GadgetUseEvent(player, cosmetic.id())).isCancelled()) {
            return;
        }
        uses.put(cosmetic.id(), now);
        switch (gadget.type()) {
            case LEAP -> {
                Vec forward = use.look().withY(0);
                Vec horizontal = forward.isZero() ? Vec.ZERO : forward.normalize().mul(LEAP_FORWARD * gadget.power());
                player.setVelocity(horizontal.withY(LEAP_UP * gadget.power()));
            }
            case GRAPPLE -> {
                Vec pull = use.hit().point().sub(use.at()).asVec();
                double speed = Math.min(GRAPPLE_MAX, GRAPPLE_BASE + pull.length() * GRAPPLE_PER_BLOCK) * gadget.power();
                player.setVelocity(pull.normalize().mul(speed).add(0, GRAPPLE_UP, 0));
            }
            default -> {
            }
        }
        List<Player> viewers = viewers(player, use.at(), player.getInstance());
        deps.effects().execute(() -> playGadget(gadget, use, viewers, true));
    }

    /**
     * Where the player uses the gadget, and for gadgets that aim, the block they look at. Tick thread: it reads the
     * map's blocks.
     */
    private Use aim(Player player, Cosmetic.Gadget gadget) {
        Pos at = player.getPosition();
        Pos eye = at.add(0, player.getEyeHeight(), 0);
        Vec look = at.direction();
        CosmeticEffects.Hit hit = null;
        CosmeticEffects.Paint paint = null;
        Instance instance = player.getInstance();
        if (instance != null && (gadget.type().needsTarget() || gadget.type() == Cosmetic.GadgetType.LIGHTNING)) {
            hit = CosmeticEffects.aim(instance, eye, look, gadget.range());
            if (hit != null && gadget.type() == Cosmetic.GadgetType.PAINTBALL) {
                paint = CosmeticEffects.paint(instance, hit.block(), gadget.power(), gadget.blocks(), random);
            }
        }
        if (gadget.type() == Cosmetic.GadgetType.TNT) {
            Vec forward = look.withY(0);
            Vec ahead = forward.isZero() ? Vec.ZERO : forward.normalize().mul(TNT_AHEAD);
            double x = at.x() + ahead.x();
            double z = at.z() + ahead.z();
            // The TNT lands on the ground in front of the player; the hit is where it explodes.
            hit = new CosmeticEffects.Hit(new Vec(Math.floor(x), at.y(), Math.floor(z)),
                    new Pos(x, Followers.ground(instance, x, at.y(), z), z));
        }
        return new Use(at, eye, look, hit, paint);
    }

    /**
     * The particles, entities, paint and sound of a gadget, for these viewers. Effects thread. {@code forReal} is
     * false for a preview: nothing touches anyone (a TNT pushes nobody).
     */
    private void playGadget(Cosmetic.Gadget gadget, Use use, List<Player> viewers, boolean forReal) {
        Pos at = use.at();
        Pos soundAt = at;
        switch (gadget.type()) {
            case FIREWORK -> {
                CosmeticEffects.Firework firework = CosmeticEffects.firework(gadget, at.add(0, 2, 0));
                send(viewers, firework.launch());
                later(CosmeticEffects.FIREWORK_FLIGHT_TICKS, () -> send(viewers, firework.explode()));
            }
            case LEAP -> {
                if (gadget.particle() != null) {
                    send(viewers, List.of(CosmeticEffects.puff(gadget.particle(), at)));
                }
            }
            case BURST -> {
                if (gadget.particle() != null) {
                    send(viewers, CosmeticEffects.burst(gadget.particle(), at, gadget.power()));
                }
            }
            case PAINTBALL -> {
                if (use.hit() != null && use.paint() != null) {
                    send(viewers, CosmeticEffects.line(net.minestom.server.particle.Particle.ITEM_SNOWBALL, use.eye(),
                            use.hit().point()));
                    send(viewers, use.paint().paint());
                    later((int) (gadget.durationMillis() / MinecraftServer.TICK_MS), () ->
                            send(viewers, use.paint().restore()));
                    soundAt = use.hit().point();
                }
            }
            case GRAPPLE -> {
                if (use.hit() != null && gadget.particle() != null) {
                    send(viewers, CosmeticEffects.line(gadget.particle(), use.eye(), use.hit().point()));
                }
            }
            case CONFETTI -> send(viewers, CosmeticEffects.confetti(gadget.colors(), use.eye(), use.look(),
                    gadget.power(), random));
            case TNT -> {
                Pos tntAt = use.hit().point();
                CosmeticEffects.Tnt tnt = CosmeticEffects.tnt(tntAt, TNT_FUSE_TICKS);
                send(viewers, tnt.spawn());
                MinecraftServer.getSchedulerManager().buildTask(() -> {
                    // On the tick thread: pushing players changes their velocity.
                    if (forReal && gadget.power() > 0) {
                        push(viewers, tntAt, gadget.power());
                    }
                    deps.effects().execute(() -> {
                        send(viewers, tnt.explode());
                        playSound(gadget, tntAt, viewers);
                    });
                }).delay(TaskSchedule.tick(TNT_FUSE_TICKS)).schedule();
                return;
            }
            case LIGHTNING -> {
                Pos strike = use.hit() != null ? use.hit().block().add(0.5, 1, 0.5).asPos() : at;
                CosmeticEffects.Lightning lightning = CosmeticEffects.lightning(strike);
                send(viewers, lightning.spawn());
                later(LIGHTNING_TICKS, () -> send(viewers, lightning.remove()));
                soundAt = strike;
            }
        }
        playSound(gadget, soundAt, viewers);
    }

    /** Runs {@code action} on the effects thread after {@code ticks}. */
    private void later(int ticks, Runnable action) {
        MinecraftServer.getSchedulerManager().buildTask(() -> deps.effects().execute(action))
                .delay(TaskSchedule.tick(Math.max(1, ticks))).schedule();
    }

    private static void playSound(Cosmetic.Gadget gadget, Pos at, List<Player> viewers) {
        if (gadget.sound() != null) {
            Sound sound = Sound.sound(gadget.sound(), Sound.Source.PLAYER, 1, 1);
            for (Player viewer : viewers) {
                if (viewer.isOnline()) {
                    viewer.playSound(sound, at.x(), at.y(), at.z());
                }
            }
        }
    }

    /**
     * Pushes the players near an exploding TNT away from it (and a little up). Only those who saw it: not players
     * hiding other players' cosmetics, nor anyone whose cosmetics are off (the PvP area). Tick thread.
     */
    private void push(List<Player> viewers, Pos center, double power) {
        for (Player viewer : viewers) {
            if (!viewer.isOnline() || suppressed(viewer)) {
                continue;
            }
            Vec away = viewer.getPosition().sub(center).asVec();
            double distance = away.length();
            if (distance > TNT_REACH) {
                continue;
            }
            Vec direction = distance < 0.1 ? new Vec(0, 1, 0) : away.normalize();
            double strength = TNT_PUSH * power * (1 - distance / TNT_REACH * 0.5);
            viewer.setVelocity(direction.mul(strength).withY(Math.max(direction.y() * strength, 6 * power)));
        }
    }

    private static void send(List<Player> viewers, List<? extends ServerPacket> packets) {
        for (Player viewer : viewers) {
            if (viewer.isOnline()) {
                packets.forEach(viewer::sendPacket);
            }
        }
    }

    /**
     * Shows a cosmetic to the player only, from the menu: a trail, pet or balloon follows them for
     * {@code preview-seconds}, a gadget plays once where they stand (a leap or grapple does not move them, a TNT pushes
     * nobody). Tick thread.
     */
    public void preview(Player player, Cosmetic cosmetic) {
        if (cosmetic.trail() != null || cosmetic.pet() != null || cosmetic.balloon() != null) {
            previews.put(player.getUuid(), new Preview(cosmetic, deps.clock().getAsLong() + config().previewMillis()));
        } else if (cosmetic.gadget() != null) {
            Use use = aim(player, cosmetic.gadget());
            deps.effects().execute(() -> playGadget(cosmetic.gadget(), use, List.of(player), false));
        }
    }

    // ---------------- Trails, on the effects thread ----------------

    /**
     * Everyone who should see {@code source}'s cosmetics at {@code at}: in the same instance, within
     * {@code view-distance}, seeing the player (visibility), and not hiding other players' cosmetics. The player
     * themselves always does.
     */
    private List<Player> viewers(Player source, Pos at, @Nullable Instance instance) {
        double range = config().viewDistance();
        double rangeSquared = range * range;
        List<Player> viewers = new ArrayList<>();
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (viewer == source) {
                viewers.add(viewer);
                continue;
            }
            if (viewer.getInstance() != instance || viewer.getPosition().distanceSquared(at) > rangeSquared) {
                continue;
            }
            if (!choices(viewer).hideOthers() && deps.canSee().test(viewer, source)) {
                viewers.add(viewer);
            }
        }
        return viewers;
    }

    /**
     * One tick of trails, pets and balloons: who draws a trail and who gets which particles within the budget, and
     * where every pet and balloon goes.
     */
    private void tick() {
        tick++;
        CosmeticsConfig config = config();
        if (configChanged) {
            configChanged = false;
            trails.clear();
            followers.clear();
        }
        budget.reset(config.perViewer(), config.perTick());
        if (!config.enabled()) {
            if (followers.count() > 0) {
                followers.clear();
            }
            return;
        }
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }
        List<Player> players = new ArrayList<>(online);
        followers.startTick(players);
        // A different player goes first every tick, so when the budget runs out it is not always the same ones
        // who are left out.
        int start = (int) (tick % players.size());
        long now = deps.clock().getAsLong();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get((start + i) % players.size());
            Pos at = player.getPosition();
            TrailState state = trails.computeIfAbsent(player.getUuid(), id -> new TrailState());
            boolean moving = state.last != null && state.last.distanceSquared(at) > MOVING_SQUARED;
            state.last = at;
            refresh(player, state);
            boolean suppressed = suppressed(player);
            Preview preview = previews.get(player.getUuid());
            if (preview != null && now >= preview.untilMillis()) {
                previews.remove(player.getUuid(), preview);
                preview = null;
            }
            Cosmetic previewed = preview == null ? null : preview.cosmetic();
            followers.update(player, CosmeticType.PET, follower(previewed, CosmeticType.PET, state.pet, suppressed),
                    previewed != null && previewed.type() == CosmeticType.PET, tick);
            followers.update(player, CosmeticType.BALLOON,
                    follower(previewed, CosmeticType.BALLOON, state.balloon, suppressed),
                    previewed != null && previewed.type() == CosmeticType.BALLOON, tick);
            if (budget.exhausted()) {
                continue;
            }
            if (previewed != null && previewed.trail() != null) {
                Cosmetic.Trail trail = previewed.trail();
                if (CosmeticEffects.due(trail.style(), tick, true)) {
                    drawFor(List.of(player), CosmeticEffects.trail(trail, at, tick));
                }
                continue;
            }
            Cosmetic trail = state.trail;
            if (trail == null || trail.trail() == null || !CosmeticEffects.due(trail.trail().style(), tick, moving)
                    || suppressed) {
                continue;
            }
            drawFor(viewers(player, at, player.getInstance()), CosmeticEffects.trail(trail.trail(), at, tick));
        }
    }

    /** The pet or balloon to show: one being previewed wins (even where cosmetics are off), else the chosen one. */
    private static @Nullable Cosmetic follower(@Nullable Cosmetic previewed, CosmeticType type, @Nullable Cosmetic chosen,
                                               boolean suppressed) {
        if (previewed != null && previewed.type() == type) {
            return previewed;
        }
        return suppressed ? null : chosen;
    }

    /**
     * The trail, pet and balloon a player shows, checking the permission and client again once a second (and when
     * the choice changes).
     */
    private void refresh(Player player, TrailState state) {
        String raw = deps.settings().get(player.getUuid()).cosmetics();
        if (raw == null) {
            state.trail = null;
            state.pet = null;
            state.balloon = null;
            state.raw = null;
            return;
        }
        if (!raw.equals(state.raw) || tick - state.checkedTick >= RECHECK_TICKS) {
            state.raw = raw;
            state.checkedTick = tick;
            state.trail = equipped(player, CosmeticType.TRAIL);
            state.pet = equipped(player, CosmeticType.PET);
            state.balloon = equipped(player, CosmeticType.BALLOON);
        }
    }

    private void drawFor(List<Player> viewers, List<ParticlePacket> packets) {
        for (Player viewer : viewers) {
            for (ParticlePacket packet : packets) {
                if (!budget.take(viewer)) {
                    break;
                }
                viewer.sendPacket(packet);
            }
        }
    }

    /** Completes once everything handed to the effects thread so far has run. For tests. */
    java.util.concurrent.CompletableFuture<Void> afterEffects() {
        java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        deps.effects().execute(() -> done.complete(null));
        return done;
    }

    /** Particle packets dropped by the limits since the start. */
    public long droppedParticles() {
        return budget.dropped();
    }

    /** Pets and balloons out right now. Effects thread (tests wait with {@link #afterEffects()}). */
    public int followerCount() {
        return followers.count();
    }

    /** The entity ids of a player's pet or balloon, empty if none. For tests, after {@link #afterEffects()}. */
    List<Integer> followerIds(Player owner, CosmeticType type) {
        return followers.idsOf(owner.getUuid(), type);
    }
}
