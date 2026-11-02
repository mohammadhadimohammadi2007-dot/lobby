package io.github.mohammadhadimohammadi2007_dot.lobby.api.service;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation.NonExtendable;

import java.util.Optional;

/**
 * Services addons offer each other. One addon provides an implementation of an interface it publishes, and other
 * addons use it without knowing that addon's classes, for example:
 * <pre>{@code
 * // the economy addon, in onEnable:
 * context.services().provide(Economy.class, new MyEconomy());
 *
 * // a shop addon that depends on it ("depends": ["economy"] in addon.json, so it sees the Economy interface):
 * context.services().get(Economy.class).ifPresent(economy -> economy.take(player, 100));
 * }</pre>
 *
 * <p>The interface must come from an addon the user depends on (or from a library both share): a class is only the
 * same class if it was loaded once. Look a service up when you need it instead of keeping it: its provider may be
 * reloaded or disabled, and then it is gone. A provider's services are removed when it is disabled. Any thread.
 */
@NonExtendable
public interface ServiceRegistry {

    /**
     * Offers {@code implementation} as the {@code type} service.
     *
     * @throws IllegalArgumentException if another addon already provides {@code type}
     */
    <T> void provide(Class<T> type, T implementation);

    /** Stops offering a service this addon provides; nothing happens if it does not. */
    void remove(Class<?> type);

    /** The {@code type} service, if an addon provides it. */
    <T> Optional<T> get(Class<T> type);

    /** The id of the addon that provides {@code type}, if any. */
    Optional<String> provider(Class<?> type);
}
