package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.NetworkState;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import org.jetbrains.annotations.Nullable;

/**
 * Groups of servers from the bridge config:
 * <ul>
 *   <li>{@code %group_online_<group>%}: players on all its servers, e.g. {@code %group_online_bedwars%};</li>
 *   <li>{@code %group_max_<group>%}: the player limits of its servers that are up, added together;</li>
 *   <li>{@code %group_status_<group>%}: {@code online}, {@code full} (every server that is up is full) or
 *       {@code offline}, for menus that look different per status.</li>
 * </ul>
 * Without the bridge, or for an unknown group, the numbers are 0 and the status is {@code offline}.
 */
final class GroupNamespace implements PlaceholderNamespace {

    private static final String ONLINE = "online_";
    private static final String MAX = "max_";
    private static final String STATUS = "status_";

    private final NetworkState network;

    GroupNamespace(NetworkState network) {
        this.network = network;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        if (params.startsWith(ONLINE) && params.length() > ONLINE.length()) {
            String group = params.substring(ONLINE.length());
            return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(network.groupOnline(group)));
        }
        if (params.startsWith(MAX) && params.length() > MAX.length()) {
            String group = params.substring(MAX.length());
            return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> String.valueOf(network.groupMaxPlayers(group)));
        }
        if (params.startsWith(STATUS) && params.length() > STATUS.length()) {
            String group = params.substring(STATUS.length());
            return Placeholder.global(BuiltinPlaceholders.SHORT_CACHE, () -> network.groupStatus(group).text());
        }
        return null;
    }
}
