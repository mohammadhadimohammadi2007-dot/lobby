package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code %server_...%}: {@code online} (this lobby), {@code max_players}, {@code name}, {@code tps},
 * {@code mspt}, and {@code status_<server>} ({@code online}/{@code offline}, from the bridge's pings).
 */
final class ServerNamespace implements PlaceholderNamespace {

    private static final String STATUS = "status_";
    static final String ONLINE = "online";
    static final String OFFLINE = "offline";

    private final ConfigManager config;
    private final ServerInfo serverInfo;
    private final NetworkState network;

    ServerNamespace(ConfigManager config, ServerInfo serverInfo, NetworkState network) {
        this.config = config;
        this.serverInfo = serverInfo;
        this.network = network;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "online" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.valueOf(MinecraftServer.getConnectionManager().getOnlinePlayerCount()));
            case "max_players" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.valueOf(config.current().config().server().maxPlayers()));
            case "name" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> config.current().config().server().name());
            case "tps" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.format(Locale.ROOT, "%.1f", serverInfo.tps()));
            case "mspt" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.format(Locale.ROOT, "%.2f", serverInfo.mspt()));
            default -> {
                if (params.startsWith(STATUS) && params.length() > STATUS.length()) {
                    String server = params.substring(STATUS.length());
                    yield Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                            () -> network.serverOnline(server) ? ONLINE : OFFLINE);
                }
                yield null;
            }
        };
    }
}
