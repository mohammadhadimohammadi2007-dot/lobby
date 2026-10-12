package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Where addons' per-player values are kept: the shared database, or files when there is none. Every
 * method blocks; callers run them on virtual threads.
 */
public interface AddonSettingsStore {

    Optional<String> load(UUID player, String addon, String key) throws Exception;

    /** Saves a value, or removes it if {@code value} is {@code null}. */
    void save(UUID player, String addon, String key, @Nullable String value) throws Exception;

    /** For logs, e.g. "database table lobby_addon_settings". */
    String describe();
}
