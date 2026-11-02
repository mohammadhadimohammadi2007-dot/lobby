package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.ServiceRegistry;

import java.util.Objects;
import java.util.Optional;

/** One addon's view of the {@link SharedServices}: what it provides is in its name and goes when it stops. */
final class ApiServiceRegistry implements ServiceRegistry {

    private final String addon;
    private final SharedServices shared;

    ApiServiceRegistry(String addon, SharedServices shared) {
        this.addon = addon;
        this.shared = shared;
    }

    @Override
    public <T> void provide(Class<T> type, T implementation) {
        Objects.requireNonNull(type, "type");
        if (!type.isInstance(Objects.requireNonNull(implementation, "implementation"))) {
            throw new IllegalArgumentException(implementation.getClass().getName() + " is not a " + type.getName());
        }
        shared.provide(addon, type, implementation);
    }

    @Override
    public void remove(Class<?> type) {
        shared.remove(addon, type);
    }

    @Override
    public <T> Optional<T> get(Class<T> type) {
        return shared.get(type).map(provided -> type.cast(provided.implementation()));
    }

    @Override
    public Optional<String> provider(Class<?> type) {
        return shared.get(type).map(SharedServices.Provided::addon);
    }

    /** Removes what this addon provides. */
    void cleanup() {
        shared.removeAll(addon);
    }
}
