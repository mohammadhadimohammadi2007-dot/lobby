package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.common.ProtocolVersions;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.Placeholder;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderNamespace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * {@code %player_...%}: {@code name}, {@code displayname}, {@code uuid}, {@code ping}, {@code world},
 * {@code protocol_version} and {@code client_version} (e.g. {@code 1.8.x}, reported by the bridge).
 */
final class PlayerNamespace implements PlaceholderNamespace {

    private final BridgeService bridge;
    private final ConfigManager config;

    PlayerNamespace(BridgeService bridge, ConfigManager config) {
        this.bridge = bridge;
        this.config = config;
    }

    @Override
    public @Nullable Placeholder lookup(String params) {
        return switch (params) {
            case "name" -> Placeholder.player(player -> player.getUsername());
            // Display names can be set from anywhere, so they are always shown as plain text.
            case "displayname" -> Placeholder.player(player -> {
                Component displayName = player.getDisplayName();
                return displayName == null ? player.getUsername() : PlainTextComponentSerializer.plainText().serialize(displayName);
            });
            case "uuid" -> Placeholder.player(player -> player.getUuid().toString());
            case "ping" -> Placeholder.player(BuiltinPlaceholders.SHORT_CACHE, player -> String.valueOf(player.getLatency()));
            // One lobby world per server; Phase 3 can return the lobby instance's name.
            case "world" -> Placeholder.player(player -> config.current().config().server().name());
            case "protocol_version" -> Placeholder.player(player -> String.valueOf(bridge.capabilities(player).protocolVersion()));
            case "client_version" -> Placeholder.player(player -> ProtocolVersions.name(bridge.capabilities(player).protocolVersion()));
            default -> null;
        };
    }
}
