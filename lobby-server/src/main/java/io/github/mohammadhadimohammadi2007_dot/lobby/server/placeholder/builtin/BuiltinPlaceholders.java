package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.builtin;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.bridge.BridgeService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.command.ServerInfo;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.ConfigManager;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.litebans.MuteService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderRegistry;

import java.time.Duration;

/**
 * Registers the placeholders that come with the lobby. Names follow popular PlaceholderAPI expansions
 * (Player, LuckPerms, Server, Bungee) so configs from Paper servers keep working.
 */
public final class BuiltinPlaceholders {

    /** How long fast-changing global values (TPS, player counts) are cached. */
    static final Duration SHORT_CACHE = Duration.ofSeconds(1);
    static final String YES = "yes";
    static final String NO = "no";

    private BuiltinPlaceholders() {
    }

    /** Everything the built-in placeholders read from. */
    public record Sources(
            ConfigManager config,
            PermissionService permissions,
            MuteService mutes,
            BridgeService bridge,
            ServerInfo serverInfo
    ) {
    }

    /** Registers {@code player_}, {@code luckperms_}, {@code server_}, {@code bungee_}, {@code group_}, {@code lobby_} and {@code litebans_}. */
    public static void registerAll(PlaceholderRegistry registry, Sources sources) {
        registry.register("player", new PlayerNamespace(sources.bridge(), sources.config()));
        registry.register("luckperms", new LuckPermsNamespace(sources.permissions()));
        registry.register("server", new ServerNamespace(sources.config(), sources.serverInfo()));
        registry.register("bungee", new BungeeNamespace(sources.bridge().networkState()));
        registry.register("group", new GroupNamespace(sources.bridge().networkState()));
        registry.register("lobby", new LobbyNamespace(sources.config(), sources.bridge().networkState()));
        registry.register("litebans", new LiteBansNamespace(sources.mutes(), sources.config()));
    }

    static String yesNo(boolean value) {
        return value ? YES : NO;
    }
}
