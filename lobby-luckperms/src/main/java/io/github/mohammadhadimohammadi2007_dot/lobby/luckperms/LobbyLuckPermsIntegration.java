package io.github.mohammadhadimohammadi2007_dot.lobby.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.luckperms.LuckPermsIntegration;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.Permissions;
import me.lucko.luckperms.minestom.LuckPermsMinestom;
import net.luckperms.api.LuckPerms;

import java.nio.file.Path;

/** Starts the LuckPerms Minestom port with settings from integrations.yml. Found through ServiceLoader. */
public final class LobbyLuckPermsIntegration implements LuckPermsIntegration {

    @Override
    public PermissionService start(Path dataDir, IntegrationsConfig.Database database,
                                   IntegrationsConfig.LuckPerms settings) {
        LuckPerms luckPerms = LuckPermsMinestom.builder(dataDir.resolve("luckperms"))
                .configurationAdapter(plugin -> new LobbyConfigAdapter(plugin, database, settings))
                // Shown as suggestions in the LuckPerms web editor.
                .permissionSuggestions(Permissions.COMMAND_SPAWN, Permissions.COMMAND_RELOAD,
                        Permissions.COMMAND_SETSPAWN, Permissions.COMMAND_INFO, Permissions.BYPASS_PROTECTION)
                .enable();
        return new LuckPermsPermissionService(luckPerms);
    }
}
