package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code %server_...%}: {@code online} (this lobby), {@code max_players}, {@code name}, {@code tps}
 * and {@code mspt}. {@code %server_status_<server>%} is added in {@code ServerStatusNamespace}.
 */
final class ServerNamespace implements PlaceholderNamespace {

    private final ConfigManager config;
    private final ServerInfo serverInfo;

    ServerNamespace(ConfigManager config, ServerInfo serverInfo) {
        this.config = config;
        this.serverInfo = serverInfo;
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
            default -> null;
        };
    }
}
