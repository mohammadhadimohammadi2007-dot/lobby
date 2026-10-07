package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PlayerMeta;
import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Permissions and rank meta from LuckPerms. Reads LuckPerms' in-memory cache only, so every call
 * is fast and safe on the tick thread.
 */
final class LuckPermsPermissionService implements PermissionService {

    private final LuckPerms luckPerms;

    LuckPermsPermissionService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        // When ranks change (on this server or, through messaging, anywhere on the network),
        // refresh the player's command list so tab completion matches their new permissions.
        luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(event.getUser().getUniqueId());
            if (player != null) {
                MinecraftServer.getSchedulerManager().scheduleNextTick(player::refreshCommands);
            }
        });
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
        return new PlayerMeta(
                meta.getPrefix() != null ? meta.getPrefix() : "",
                meta.getSuffix() != null ? meta.getSuffix() : "",
                primaryGroup,
                values);
    }

    @Override
    public void shutdown() {
        LuckPermsMinestom.disable();
    }
}
