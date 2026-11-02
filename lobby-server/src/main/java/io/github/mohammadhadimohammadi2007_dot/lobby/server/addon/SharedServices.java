package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The services addons provide each other, shared by all of them: by interface, which addon provides it and how. */
final class SharedServices {

    /** One provided service. */
    record Provided(String addon, Object implementation) {
    }

    private final Map<Class<?>, Provided> services = new ConcurrentHashMap<>();

    /** Adds a service; throws if another addon provides that type already (the same addon replaces its own). */
    void provide(String addon, Class<?> type, Object implementation) {
        services.compute(type, (key, current) -> {
            if (current != null && !current.addon().equals(addon)) {
                throw new IllegalArgumentException("The service " + type.getName() + " is already provided by addon '"
                        + current.addon() + "'");
            }
            return new Provided(addon, implementation);
        });
    }

    void remove(String addon, Class<?> type) {
        services.computeIfPresent(type, (key, current) -> current.addon().equals(addon) ? null : current);
    }

    Optional<Provided> get(Class<?> type) {
        return Optional.ofNullable(services.get(type));
    }

    /** Removes every service of an addon that stopped. */
    void removeAll(String addon) {
        services.values().removeIf(provided -> provided.addon().equals(addon));
    }
}
