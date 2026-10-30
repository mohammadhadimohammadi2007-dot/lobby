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
 * Trails, hats and gadgets. Players pick them in the cosmetics menu; their choice is saved with their other
 * settings. A cosmetic shows only while the player may use it (its permission) and their client can show it
 * ({@code min-version}); a choice that stops qualifying is kept but not shown.
 *
 * <p>Everything a viewer sees of someone's cosmetics, except the hat (a real item on the head), is particles and
 * packets sent from the {@code lobby-effects} thread: never from the tick thread. Only players within
 * {@code view-distance} who can see the player (player visibility) and do not hide other players' cosmetics get
 * them, and a {@link ParticleBudget} caps what one player receives and what the server sends per tick. Cosmetics
 * are off for players in the PvP area (and anywhere else a {@link #suppressWhen} rule says so).
 */
public final class CosmeticsService {

    /** Ticks between two checks of a player's trail permission and client version. */
    private static final int RECHECK_TICKS = 20;
    /** A player moved this far (squared, blocks) since the last tick counts as walking, for feet trails. */
    private static final double MOVING_SQUARED = 0.0025;
    private static final double LEAP_FORWARD = 12;
    private static final double LEAP_UP = 8;

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

    /** What the effects thread knows about one player's trail. Effects thread only. */
    private static final class TrailState {
        @Nullable Cosmetic trail;
        @Nullable String raw;
        long checkedTick = Long.MIN_VALUE;
        @Nullable Pos last;
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
    private long tick;
    private volatile boolean configChanged;
    private final AtomicBoolean ticking = new AtomicBoolean();
    private @Nullable Task task;

    public CosmeticsService(Dependencies deps) {
        this.deps = deps;
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
        deps.effects().execute(() -> trails.remove(player));
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
        if (type != CosmeticType.TRAIL) {
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

    /** The hat and the gadget item for the hotbar, which puts them on with the other items. */
    public HotbarService.Extras hotbarExtras() {
        return new HotbarService.Extras() {
            @Override
            public net.minestom.server.item.@Nullable ItemStack hat(Player player) {
                return CosmeticsService.this.hat(player);
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
        if (LobbyEvents.active() && LobbyEvents.fire(new GadgetUseEvent(player, cosmetic.id())).isCancelled()) {
            return;
        }
        uses.put(cosmetic.id(), now);
        if (gadget.type() == Cosmetic.GadgetType.LEAP) {
            Vec forward = player.getPosition().direction().withY(0);
            Vec horizontal = forward.isZero() ? Vec.ZERO : forward.normalize().mul(LEAP_FORWARD * gadget.power());
            player.setVelocity(horizontal.withY(LEAP_UP * gadget.power()));
        }
        Pos at = player.getPosition();
        Instance instance = player.getInstance();
        deps.effects().execute(() -> playGadget(gadget, at, viewers(player, at, instance)));
    }

    /** The particles, firework and sound of a gadget, for these viewers. Effects thread. */
    private void playGadget(Cosmetic.Gadget gadget, Pos at, List<Player> viewers) {
        switch (gadget.type()) {
            case FIREWORK -> {
                CosmeticEffects.Firework firework = CosmeticEffects.firework(gadget, at.add(0, 2, 0));
                send(viewers, firework.launch());
                MinecraftServer.getSchedulerManager().buildTask(() -> deps.effects().execute(() ->
                                send(viewers, firework.explode())))
                        .delay(TaskSchedule.tick(CosmeticEffects.FIREWORK_FLIGHT_TICKS)).schedule();
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
        }
        if (gadget.sound() != null) {
            Sound sound = Sound.sound(gadget.sound(), Sound.Source.PLAYER, 1, 1);
            for (Player viewer : viewers) {
                viewer.playSound(sound, at.x(), at.y(), at.z());
            }
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
     * Shows a trail or gadget to the player only, from the menu: a trail follows them for {@code preview-seconds}, a
     * gadget plays once where they stand (a leap does not throw them). Tick thread.
     */
    public void preview(Player player, Cosmetic cosmetic) {
        if (cosmetic.trail() != null) {
            previews.put(player.getUuid(), new Preview(cosmetic, deps.clock().getAsLong() + config().previewMillis()));
        } else if (cosmetic.gadget() != null) {
            Pos at = player.getPosition();
            deps.effects().execute(() -> playGadget(cosmetic.gadget(), at, List.of(player)));
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

    /** One tick of trails: who draws one, and who gets which packets within the budget. */
    private void tick() {
        tick++;
        CosmeticsConfig config = config();
        if (configChanged) {
            configChanged = false;
            trails.clear();
        }
        budget.reset(config.perViewer(), config.perTick());
        if (!config.enabled()) {
            return;
        }
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }
        List<Player> players = new ArrayList<>(online);
        // A different player goes first every tick, so when the budget runs out it is not always the same ones
        // who are left out.
        int start = (int) (tick % players.size());
        long now = deps.clock().getAsLong();
        for (int i = 0; i < players.size() && !budget.exhausted(); i++) {
            Player player = players.get((start + i) % players.size());
            Pos at = player.getPosition();
            TrailState state = trails.computeIfAbsent(player.getUuid(), id -> new TrailState());
            boolean moving = state.last != null && state.last.distanceSquared(at) > MOVING_SQUARED;
            state.last = at;
            Preview preview = previews.get(player.getUuid());
            if (preview != null) {
                if (now >= preview.untilMillis()) {
                    previews.remove(player.getUuid(), preview);
                } else {
                    Cosmetic.Trail trail = preview.cosmetic().trail();
                    if (trail != null && CosmeticEffects.due(trail.style(), tick, true)) {
                        drawFor(List.of(player), CosmeticEffects.trail(trail, at, tick));
                    }
                    continue;
                }
            }
            Cosmetic trail = currentTrail(player, state);
            if (trail == null || trail.trail() == null || !CosmeticEffects.due(trail.trail().style(), tick, moving)
                    || suppressed(player)) {
                continue;
            }
            drawFor(viewers(player, at, player.getInstance()), CosmeticEffects.trail(trail.trail(), at, tick));
        }
    }

    /** The trail to draw, checking the permission and client again once a second (and when the choice changes). */
    private @Nullable Cosmetic currentTrail(Player player, TrailState state) {
        String raw = deps.settings().get(player.getUuid()).cosmetics();
        if (raw == null) {
            state.trail = null;
            state.raw = null;
            return null;
        }
        if (!raw.equals(state.raw) || tick - state.checkedTick >= RECHECK_TICKS) {
            state.raw = raw;
            state.checkedTick = tick;
            state.trail = equipped(player, CosmeticType.TRAIL);
        }
        return state.trail;
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
}
