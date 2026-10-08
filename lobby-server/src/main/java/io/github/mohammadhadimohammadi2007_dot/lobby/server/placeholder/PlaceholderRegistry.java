package io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knows every placeholder namespace. Later phases add their own with {@link #register}.
 *
 * <p>Thread-safe. Lookups are remembered, so each distinct placeholder text is resolved to a
 * {@link Placeholder} only once.
 */
public final class PlaceholderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceholderRegistry.class);
    /** Stops a flood of distinct unknown placeholders from growing the lookup cache forever. */
    private static final int MAX_REMEMBERED_LOOKUPS = 10_000;

    private final Map<String, PlaceholderNamespace> namespaces = new ConcurrentHashMap<>();
    private final Map<String, Optional<Placeholder>> lookups = new ConcurrentHashMap<>();
    private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();

    /**
     * Adds a namespace. For {@code %myplugin_something%} register {@code "myplugin"}.
     *
     * @throws IllegalArgumentException if the name is invalid or already taken
     */
    public void register(String namespace, PlaceholderNamespace resolver) {
        String name = namespace.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("Namespace must be letters and digits only: " + namespace);
        }
        if (namespaces.putIfAbsent(name, resolver) != null) {
            throw new IllegalArgumentException("Placeholder namespace already registered: " + namespace);
        }
        lookups.clear();
    }

    /**
     * Finds the placeholder for a key like {@code luckperms_prefix} (without the {@code %} signs).
     * Unknown keys return {@code null} and are logged once at debug level.
     */
    public @Nullable Placeholder find(String key) {
        Optional<Placeholder> known = lookups.get(key);
        if (known == null) {
            known = Optional.ofNullable(resolve(key));
            if (lookups.size() < MAX_REMEMBERED_LOOKUPS) {
                lookups.put(key, known);
            }
            if (known.isEmpty() && reportedUnknown.size() < MAX_REMEMBERED_LOOKUPS && reportedUnknown.add(key)) {
                LOGGER.debug("Unknown placeholder %{}% is left as it is", key);
            }
        }
        return known.orElse(null);
    }

    private @Nullable Placeholder resolve(String key) {
        int separator = key.indexOf('_');
        if (separator <= 0 || separator == key.length() - 1) {
            return null;
        }
        PlaceholderNamespace namespace = namespaces.get(key.substring(0, separator).toLowerCase(Locale.ROOT));
        if (namespace == null) {
            return null;
        }
        try {
            return namespace.lookup(key.substring(separator + 1));
        } catch (RuntimeException e) {
            LOGGER.warn("Placeholder namespace failed to look up %{}%", key, e);
            return null;
        }
    }
}
