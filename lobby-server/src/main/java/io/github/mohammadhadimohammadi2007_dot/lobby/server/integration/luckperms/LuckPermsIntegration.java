package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.luckperms;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.player.PermissionService;

import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Starts LuckPerms. Implemented by the optional {@code lobby-luckperms} module, which is only part of
 * {@code lobby-server.jar} when it is built with {@code -PwithLuckPerms}. The core never depends on
 * LuckPerms classes directly, so a build without it works the same in every other way.
 */
public interface LuckPermsIntegration {

    /**
     * Starts LuckPerms against the network database and returns a permission service backed by it.
     * Blocking; called once at startup before players can join.
     *
     * @param dataDir   folder for LuckPerms' own files (it creates {@code luckperms/} inside)
     * @param database  shared database settings from integrations.yml
     * @param luckPerms the {@code luckperms:} section of integrations.yml
     * @throws Exception if LuckPerms cannot start; the lobby then falls back to the operators list
     */
    PermissionService start(Path dataDir, IntegrationsConfig.Database database, IntegrationsConfig.LuckPerms luckPerms)
            throws Exception;

    /** The LuckPerms module included in this build, if any. */
    static Optional<LuckPermsIntegration> find() {
        return ServiceLoader.load(LuckPermsIntegration.class).findFirst();
    }
}
