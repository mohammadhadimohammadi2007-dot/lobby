package io.github.mohammadhadimohammadi2007_dot.lobby.server.display;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.ClientObjectRenderer;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.DisplayLoad;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.entity.MeasuredThread;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.team.TeamManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

/**
 * The scoreboard, tab list, nametags, boss bar, action bar and join title, from {@code display.yml}.
 *
 * <p>Everything runs on its own thread, {@code lobby-board}, never on the tick thread, and apart from the
 * holograms and NPCs (which have the display thread of the {@link ClientObjectRenderer}): a busy moment
 * there, such as players walking past many NPCs, never makes the scoreboard late. A refresh is started
 * every {@link #STEP_TICKS} ticks; each part decides whether it is due. If the thread is still busy with
 * the previous one, the new one is skipped instead of queueing up.
 */
public final class DisplayService {

    /** How often a refresh starts; the shortest interval anything can have. */
    static final int STEP_TICKS = SidebarConfig.MIN_INTERVAL_TICKS;

    private final ConfigManager config;
    private final MeasuredThread board = new MeasuredThread("lobby-board");
    private final TextCache text;
    private final SidebarService sidebar;
    private final TabListService tab;
    private final PlayerTeams playerTeams;
    private final BarsService bars;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger skipped = new AtomicInteger();
    private @Nullable Task task;
    private volatile long ticks;
    private long previousTicks = -1;
    private DisplayConfig applied;

    /**
     * @param protocolOf each player's real protocol version (from the bridge)
     */
    public DisplayService(ConfigManager config, LobbyText text, PermissionService permissions, TeamManager teams,
                          ToIntFunction<Player> protocolOf) {
        this.config = config;
        ClientTiers tiers = new ClientTiers(protocolOf);
        this.text = new TextCache(text, tiers);
        this.sidebar = new SidebarService(new SidebarPackets(teams), tiers, permissions);
        this.tab = new TabListService(tiers, text);
        this.playerTeams = new PlayerTeams(teams, permissions);
        this.bars = new BarsService(permissions);
        this.applied = config.current().display();
    }

    /** Starts refreshing, shows the join title and forgets players who leave. */
    public void register(EventNode<PlayerEvent> node) {
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                bars.scheduleJoinTitle(config.current().display().bars().joinTitle(), event.getPlayer(), text,
                        board::execute);
            }
        });
        node.addListener(PlayerDisconnectEvent.class, event -> {
            UUID player = event.getPlayer().getUuid();
            board.execute(() -> forget(player));
        });
        task = MinecraftServer.getSchedulerManager().buildTask(this::startRefresh)
                .repeat(TaskSchedule.tick(STEP_TICKS))
                .schedule();
    }

    /** Called on the tick thread: hands one refresh to the board thread, unless one is still running. */
    private void startRefresh() {
        ticks += STEP_TICKS;
        if (!running.compareAndSet(false, true)) {
            skipped.incrementAndGet();
            return;
        }
        long now = ticks;
        board.execute(() -> {
            try {
                refresh(MinecraftServer.getConnectionManager().getOnlinePlayers(), now);
            } finally {
                running.set(false);
            }
        });
    }

    /** One refresh of everything that is due. Board thread only. */
    void refresh(Collection<Player> online, long now) {
        DisplayConfig current = config.current().display();
        if (current != applied) {
            apply(current, online);
        }
        text.clear();
        long previous = previousTicks < 0 ? now - STEP_TICKS : previousTicks;
        // Anything due "since before the first refresh" counts as due now.
        boolean first = previousTicks < 0;
        previousTicks = now;
        sidebar.refresh(current.sidebar(), online, text, first ? -1 : previous, now);
        TabConfig tabConfig = current.tab();
        tab.headerAndFooter(tabConfig, online, text, previous, now);
        if (first || Stagger.due(previous, now, tabConfig.intervalTicks())) {
            tab.refresh(tabConfig, online, text);
            playerTeams.refresh(tabConfig, online, text);
        }
        bars.refresh(current.bars(), online, text, first ? -1 : previous, now);
    }

    /** A reloaded display.yml: what was turned off is taken away, the rest is rebuilt on the next refresh. */
    private void apply(DisplayConfig next, Collection<Player> online) {
        if (!next.sidebar().enabled()) {
            sidebar.hideAll(online);
        }
        if (!next.tab().enabled()) {
            tab.reset(online);
        }
        if (!next.bars().bossBar().enabled()) {
            bars.hideAll(online);
        }
        text.forgetTemplates();
        applied = next;
    }

    private void forget(UUID player) {
        sidebar.forget(player);
        tab.forget(player);
        bars.forget(player);
    }

    /** Runs one refresh now on the board thread and waits for it, as if {@code advance} ticks passed. For tests. */
    public void refreshNow(int advance) {
        ticks += advance;
        long now = ticks;
        CompletableFuture<Void> done = new CompletableFuture<>();
        board.execute(() -> {
            try {
                refresh(MinecraftServer.getConnectionManager().getOnlinePlayers(), now);
                done.complete(null);
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        try {
            done.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("display refresh failed", e);
        }
    }

    /** How busy the board thread was over the last minute, for {@code /lobby info}. */
    public DisplayLoad.Snapshot load() {
        return board.load();
    }

    /** Refreshes skipped because the board thread was still busy, for /lobby info and tests. */
    public int skippedRefreshes() {
        return skipped.get();
    }

    /** Numbers for tests and measurements: renders, sidebar lines sent, tab name packets. */
    public List<Integer> counters() {
        return List.of(text.renders(), sidebar.linesSent(), tab.namePackets());
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        board.shutdown();
    }
}
