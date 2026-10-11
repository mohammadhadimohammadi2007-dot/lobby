package io.github.mohammadhadimohammadi2007_dot.lobby.server.connection;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.OpList;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.network.player.GameProfile;

/**
 * In standalone offline mode Minestom keeps whatever UUID the client sends. A client can send anyone's
 * UUID, so the lobby gives every player the UUID a vanilla or Bukkit server would:
 * {@code OfflinePlayer:<name>}. Saved settings and operators then belong to the name, the same on every
 * offline server.
 */
public final class OfflineUuids {

    private OfflineUuids() {
    }

    /** Registers the listener. Must come before any other pre-login listener that reads the UUID. */
    public static void register(EventNode<Event> node) {
        node.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            GameProfile profile = event.getGameProfile();
            event.setGameProfile(new GameProfile(OpList.offlineUuid(profile.name()), profile.name(), profile.properties()));
        });
    }
}
