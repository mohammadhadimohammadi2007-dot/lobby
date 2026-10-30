package io.github.mohammadhadimohammadi2007_dot.lobby.server.vanish;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.VanishChangeEvent;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.LobbyEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatSettingsService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.MessageKey;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.LobbyText;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Vanish ({@code /vanish}): a staff member disappears for everyone without {@code lobby.vanish.see}. They are not
 * drawn, not in the tab list, not counted by {@code %server_online%}, {@code %lobby_online%}, the server list or
 * {@code /list}, get no join or quit message, and have no cosmetics. They cannot enter the PvP area while vanished.
 *
 * <p>Being vanished is saved with the player's settings, so a vanished staff member who rejoins is hidden from the
 * first moment: the saved choice is read during login, before they appear. The network's own counts (the proxy's
 * player list, {@code %bungee_...%}) come from the proxy and still count them.
 */
public final class VanishService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanishService.class);
    /** How long a login waits for the saved settings of someone who may vanish. */
    private static final long LOAD_WAIT_SECONDS = 3;
    /** Ticks between the "you are vanished" reminders above the hotbar. */
    private static final int REMINDER_TICKS = 40;

    /** What vanishing changes elsewhere in the lobby. */
    public record Hooks(Consumer<Player> refreshVisibility, Predicate<Player> inPvpArea) {
    }

    /** The outcome of {@link #set}. */
    public enum Change { DONE, UNCHANGED, IN_PVP_AREA, CANCELLED }

    private final LobbyText text;
    private final PermissionService permissions;
    private final ChatSettingsService settings;
    private volatile Hooks hooks = new Hooks(player -> { }, player -> false);
    private @Nullable Task reminder;

    public VanishService(LobbyText text, PermissionService permissions, ChatSettingsService settings) {
        this.text = text;
        this.permissions = permissions;
        this.settings = settings;
    }

    public void hooks(Hooks value) {
        hooks = value;
    }

    public void register(EventNode<PlayerEvent> node) {
        node.addListener(AsyncPlayerConfigurationEvent.class, this::onLogin);
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                // On the next tick, after the client got everyone's tab entry.
                Player player = event.getPlayer();
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    if (player.isOnline()) {
                        afterJoin(player);
                    }
                });
            }
        });
        // A tick later, so every other listener (no quit message, no count) still sees them as vanished.
        node.addListener(PlayerDisconnectEvent.class, event -> MinecraftServer.getSchedulerManager().scheduleNextTick(
                () -> Vanished.set(event.getPlayer().getUuid(), false)));
        reminder = MinecraftServer.getSchedulerManager().buildTask(this::remind)
                .repeat(TaskSchedule.tick(REMINDER_TICKS)).schedule();
    }

    /**
     * During login (a virtual thread, before the player appears anywhere): someone allowed to vanish who was
     * vanished when they left is vanished again before anyone can see them.
     */
    private void onLogin(AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();
        if (!permissions.hasPermission(player, Permissions.COMMAND_VANISH)) {
            return;
        }
        settings.load(player.getUuid());
        try {
            settings.whenLoaded(player.getUuid()).get(LOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Could not read whether {} was vanished in time; they join visible", player.getUsername());
            return;
        }
        if (settings.get(player.getUuid()).vanished()) {
            Vanished.set(player.getUuid(), true);
        }
    }

    /** Hides a vanished player who just joined from the tab lists, and the vanished ones from a new viewer's. */
    private void afterJoin(Player player) {
        if (Vanished.is(player)) {
            player.sendMessage(text.message(MessageKey.VANISH_STILL, player));
            tab(player, false);
        }
        if (!canSeeVanished(player)) {
            List<PlayerInfoUpdatePacket.Entry> hidden = new ArrayList<>();
            for (Player other : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (other != player && Vanished.is(other)) {
                    hidden.add(entry(other, false));
                }
            }
            if (!hidden.isEmpty()) {
                player.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LISTED, hidden));
            }
        }
    }

    public boolean isVanished(Player player) {
        return Vanished.is(player);
    }

    /** True if the player may see vanished players. */
    public boolean canSeeVanished(Player viewer) {
        return permissions.hasPermission(viewer, Permissions.VANISH_SEE);
    }

    /**
     * Vanishes the player or makes them visible again, saves it, and tells them. Tick thread.
     *
     * @return what happened; a player in the PvP area cannot vanish
     */
    public Change set(Player player, boolean vanished) {
        if (Vanished.is(player) == vanished) {
            return Change.UNCHANGED;
        }
        if (vanished && hooks.inPvpArea().test(player)) {
            return Change.IN_PVP_AREA;
        }
        if (LobbyEvents.active() && LobbyEvents.fire(new VanishChangeEvent(player, vanished)).isCancelled()) {
            return Change.CANCELLED;
        }
        Vanished.set(player.getUuid(), vanished);
        settings.update(player.getUuid(), current -> current.withVanished(vanished));
        hooks.refreshVisibility().accept(player);
        tab(player, !vanished);
        player.sendMessage(text.message(vanished ? MessageKey.VANISH_ON : MessageKey.VANISH_OFF, player));
        return Change.DONE;
    }

    /** Lists or unlists the player in the tab list of everyone who may not see vanished players. */
    private void tab(Player target, boolean listed) {
        PlayerInfoUpdatePacket packet = new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                List.of(entry(target, listed)));
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (viewer != target && !canSeeVanished(viewer)) {
                viewer.sendPacket(packet);
            }
        }
    }

    private static PlayerInfoUpdatePacket.Entry entry(Player target, boolean listed) {
        return new PlayerInfoUpdatePacket.Entry(target.getUuid(), target.getUsername(), List.of(), listed, 0,
                GameMode.SURVIVAL, null, null, 0, true);
    }

    /** Above the hotbar of every vanished player, so they never forget. */
    private void remind() {
        if (Vanished.count() == 0) {
            return;
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (Vanished.is(player)) {
                player.sendActionBar(text.message(MessageKey.VANISH_REMINDER, player));
            }
        }
    }

    public void shutdown() {
        if (reminder != null) {
            reminder.cancel();
        }
    }
}
