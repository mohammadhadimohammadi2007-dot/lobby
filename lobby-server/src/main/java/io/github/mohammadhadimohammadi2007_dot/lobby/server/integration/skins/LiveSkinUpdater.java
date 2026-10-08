package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.skins;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a skin the proxy reports as changed, for example after a player ran SkinsRestorer's
 * {@code /skin} there. The player and everyone who can see them get the new skin at once, without
 * reconnecting.
 */
public final class LiveSkinUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveSkinUpdater.class);

    private LiveSkinUpdater() {
    }

    /** Starts listening for skin changes from the proxy. */
    public static void register(BridgeService bridge) {
        bridge.onSkinUpdate(update -> {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(update.playerId());
            if (player == null) {
                return;
            }
            PlayerSkin skin = new PlayerSkin(update.value(), update.signature().isEmpty() ? null : update.signature());
            // setSkin respawns the player for everybody, so it has to run on the tick thread.
            Async.onTickThread(() -> {
                if (player.isOnline()) {
                    player.setSkin(skin);
                    LOGGER.debug("Applied a new skin for {}", player.getUsername());
                }
            });
        });
    }
}
