package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

/**
 * {@code %bungee_total%} (players on the whole network) and {@code %bungee_<server>%} (players on one backend),
 * the same names as PlaceholderAPI's Bungee expansion. Needs the bridge; without it every count is 0.
 */
final class BungeeNamespace implements PlaceholderNamespace {

    private final NetworkState network;

    BungeeNamespace(NetworkState network) {
        this.network = network;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        if (params.equals("total")) {
            return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(network.totalOnline()));
        }
        return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(network.online(params)));
    }
}
