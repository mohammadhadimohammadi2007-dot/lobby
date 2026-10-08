package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.instance.LobbyInstanceInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

/**
 * The lobby instances of this server: {@code %lobby_id%} (which lobby the player is in),
 * {@code %lobby_count%} (how many there are), {@code %lobby_online%} (players in the player's lobby) and
 * {@code %lobby_online_2%} (players in lobby 2).
 *
 * <p>Also {@code %lobby_name%} (this server's name from config.yml) and {@code %lobby_servers%} (how many
 * lobby servers the network has: the size of the {@code lobbies} group from the bridge, or 1 without it).
 */
final class LobbyNamespace implements PlaceholderNamespace {

    /** Bridge group that lists all lobby servers. */
    static final String LOBBIES_GROUP = "lobbies";
    private static final String ONLINE_IN = "online_";

    private final ConfigManager config;
    private final NetworkState network;
    private final LobbyInstanceInfo lobbies;

    LobbyNamespace(ConfigManager config, NetworkState network, LobbyInstanceInfo lobbies) {
        this.config = config;
        this.network = network;
        this.lobbies = lobbies;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        if (params.startsWith(ONLINE_IN)) {
            return onlineIn(params.substring(ONLINE_IN.length()));
        }
        return switch (params) {
            case "id" -> Placeholder.player(BuiltinPlaceholders.SHORT_CACHE,
                    player -> String.valueOf(lobbies.numberOf(player)));
            case "count" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(lobbies.count()));
            case "online" -> Placeholder.player(BuiltinPlaceholders.SHORT_CACHE,
                    player -> String.valueOf(lobbies.onlineIn(lobbies.numberOf(player))));
            case "name" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> config.current().config().server().name());
            case "servers" -> Placeholder.global(BuiltinPlaceholders.SHORT_CACHE,
                    () -> String.valueOf(Math.max(1, network.group(LOBBIES_GROUP).size())));
            default -> null;
        };
    }

    /** {@code %lobby_online_<number>%}, or {@code null} if that is not a number. */
    private @Nullable Placeholder onlineIn(String numberText) {
        int number;
        try {
            number = Integer.parseInt(numberText);
        } catch (NumberFormatException e) {
            return null;
        }
        return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(lobbies.onlineIn(number)));
    }
}
