package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

/**
 * {@code %group_online_<group>%}: players on all servers of a group from the bridge config,
 * e.g. {@code %group_online_bedwars%}. 0 without the bridge or for an unknown group.
 */
final class GroupNamespace implements PlaceholderNamespace {

    private static final String ONLINE = "online_";

    private final NetworkState network;

    GroupNamespace(NetworkState network) {
        this.network = network;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        if (!params.startsWith(ONLINE) || params.length() == ONLINE.length()) {
            return null;
        }
        String group = params.substring(ONLINE.length());
        return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(network.groupOnline(group)));
    }
}
