package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

/**
 * {@code %lobby_id%} (this lobby's name from config.yml) and {@code %lobby_count%} (number of lobby servers:
 * the size of the {@code lobbies} group from the bridge config, or 1 without it).
 */
final class LobbyNamespace implements PlaceholderNamespace {

    /** Bridge group that lists all lobby servers. */
    static final String LOBBIES_GROUP = "lobbies";

    private final ConfigManager config;
    private final NetworkState network;

    LobbyNamespace(ConfigManager config, NetworkState network) {
        this.config = config;
        this.network = network;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "id" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> config.current().config().server().name());
            case "count" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.valueOf(Math.max(1, network.group(LOBBIES_GROUP).size())));
            default -> null;
        };
    }
}
