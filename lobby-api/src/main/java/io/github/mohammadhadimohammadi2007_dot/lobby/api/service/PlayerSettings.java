package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Small text values saved per player, kept apart for each addon: in the lobby's database when it has one
 * (shared by every lobby server of the network), otherwise in files under data/addon-settings/. For larger
 * data an addon should use its own storage.
 *
 * <p>Both methods work in the background and never block the caller.
 */
public interface PlayerSettings {

    /** Longest key: lowercase letters, digits, {@code .}, {@code -} and {@code _}. */
    int MAX_KEY_LENGTH = 64;
    /** Longest value. */
    int MAX_VALUE_LENGTH = 4096;

    /** The saved value, or empty. */
    CompletableFuture<Optional<String>> get(UUID player, String key);

    /**
     * Saves a value; {@code null} removes it.
     *
     * @throws IllegalArgumentException if the key or value is too long or the key has other characters
     */
    CompletableFuture<Void> set(UUID player, String key, @Nullable String value);
}
