package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Permissions and rank meta from LuckPerms. Reads LuckPerms' in-memory cache only, so every call
 * is fast and safe on the tick thread.
 */
final class LuckPermsPermissionService implements PermissionService {

    private final LuckPerms luckPerms;
    private final List<Consumer<UUID>> changeListeners = new CopyOnWriteArrayList<>();

    LuckPermsPermissionService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        // Fired when a user's cached data is rebuilt: after a rank change on this server or, through
        // messaging, anywhere on the network.
        luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
            UUID playerId = event.getUser().getUniqueId();
            changeListeners.forEach(listener -> listener.accept(playerId));
            // Refresh the command list so tab completion matches the new permissions.
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
            if (player != null) {
                MinecraftServer.getSchedulerManager().scheduleNextTick(player::refreshCommands);
            }
        });
    }

    @Override
    public void onMetaChange(Consumer<UUID> listener) {
        changeListeners.add(listener);
    }

    /** The LuckPerms API, for tests and later phases. */
    LuckPerms api() {
        return luckPerms;
    }

    @Override
    public String name() {
        return "LuckPerms";
    }

    @Override
    public boolean hasPermission(Player player, String permission) {
        User user = luckPerms.getUserManager().getUser(player.getUuid());
        return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    @Override
    public PlayerMeta meta(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUuid());
        if (user == null) {
            return PlayerMeta.EMPTY;
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : meta.getMeta().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                values.put(entry.getKey(), entry.getValue().getFirst());
            }
        }
        String primaryGroup = meta.getPrimaryGroup() != null ? meta.getPrimaryGroup() : user.getPrimaryGroup();
        // Groups are always loaded in memory, so this is a map lookup.
        Group group = luckPerms.getGroupManager().getGroup(primaryGroup);
        int weight = group == null ? 0 : group.getWeight().orElse(0);
        return new PlayerMeta(
                meta.getPrefix() != null ? meta.getPrefix() : "",
                meta.getSuffix() != null ? meta.getSuffix() : "",
                primaryGroup,
                values,
                weight);
    }

    @Override
    public void shutdown() {
        LuckPermsMinestom.disable();
    }
}
