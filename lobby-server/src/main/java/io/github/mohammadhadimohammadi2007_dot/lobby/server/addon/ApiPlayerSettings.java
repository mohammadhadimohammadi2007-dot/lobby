package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.PlayerSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.addon.settings.AddonSettingsStore;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** An addon's per-player values, kept under the addon's id in the lobby's settings store. */
final class ApiPlayerSettings implements PlayerSettings {

    private final AddonContextImpl context;
    private final AddonSettingsStore store;

    ApiPlayerSettings(AddonContextImpl context, AddonSettingsStore store) {
        this.context = context;
        this.store = store;
    }

    @Override
    public CompletableFuture<Optional<String>> get(UUID player, String key) {
        checkKey(key);
        String addon = context.description().id();
        return Async.supply(() -> {
            try {
                return store.load(player, addon, key);
            } catch (Exception e) {
                throw new CompletionException("Cannot read the setting '" + key + "': " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> set(UUID player, String key, @Nullable String value) {
        checkKey(key);
        if (value != null && value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Values may be up to " + MAX_VALUE_LENGTH + " characters long");
        }
        String addon = context.description().id();
        return Async.supply(() -> {
            try {
                store.save(player, addon, key, value);
                return null;
            } catch (Exception e) {
                throw new CompletionException("Cannot save the setting '" + key + "': " + e.getMessage(), e);
            }
        });
    }

    private static void checkKey(String key) {
        if (!key.matches("[a-z0-9._-]{1," + MAX_KEY_LENGTH + "}")) {
            throw new IllegalArgumentException("Setting names are up to " + MAX_KEY_LENGTH
                    + " lowercase letters, digits, '.', '-' and '_': " + key);
        }
    }
}
