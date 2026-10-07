package io.github.mohammadhadimohammadi2007_dot.lobby.server.protection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.LobbyConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

import java.util.function.Predicate;

/**
 * Applies the {@code protection:} section of config.yml. Players with
 * {@link Permissions#BYPASS_PROTECTION} are never blocked.
 */
public final class ProtectionListener {

    /** Full food bar and saturation in vanilla. */
    private static final int MAX_FOOD = 20;
    private static final float MAX_SATURATION = 5f;

    private final ConfigManager configManager;
    private final PermissionService permissions;

    public ProtectionListener(ConfigManager configManager, PermissionService permissions) {
        this.configManager = configManager;
        this.permissions = permissions;
    }

    /** Registers the listeners on {@code node}. */
    public void register(EventNode<Event> node) {
        node.addListener(PlayerBlockBreakEvent.class, event -> {
            if (blocked(event.getPlayer(), LobbyConfig.Protection::blockBreak)) {
                event.setCancelled(true);
            }
        });
        node.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (blocked(event.getPlayer(), LobbyConfig.Protection::blockPlace)) {
                event.setCancelled(true);
            }
        });
        node.addListener(ItemDropEvent.class, event -> {
            if (event.getEntity() instanceof Player player && blocked(player, LobbyConfig.Protection::itemDrop)) {
                event.setCancelled(true);
            }
        });
        node.addListener(EntityDamageEvent.class, event -> {
            if (event.getEntity() instanceof Player player && blocked(player, LobbyConfig.Protection::damage)) {
                event.setCancelled(true);
            }
        });
        // Minestom has no hunger of its own; this keeps the bar full in case anything else changes it.
        node.addListener(PlayerSpawnEvent.class, event -> {
            if (blocked(event.getPlayer(), LobbyConfig.Protection::hunger)) {
                event.getPlayer().setFood(MAX_FOOD);
                event.getPlayer().setFoodSaturation(MAX_SATURATION);
            }
        });
    }

    /** True if the setting forbids the action and the player has no bypass permission. */
    private boolean blocked(Player player, Predicate<LobbyConfig.Protection> allowed) {
        return !allowed.test(configManager.current().config().protection())
                && !permissions.hasPermission(player, Permissions.BYPASS_PROTECTION);
    }
}
