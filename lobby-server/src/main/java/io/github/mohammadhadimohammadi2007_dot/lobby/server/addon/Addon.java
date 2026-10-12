package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/** One addon the lobby knows about: its jar, where it stands, and while it runs its code and registrations. */
final class Addon {

    private final Path jar;
    private final @Nullable AddonDescription description;
    private volatile AddonState state;
    private volatile String detail;
    private @Nullable AddonClassLoader loader;
    private @Nullable LobbyAddon instance;
    private @Nullable AddonContextImpl context;

    Addon(Path jar, @Nullable AddonDescription description, AddonState state, String detail) {
        this.jar = jar;
        this.description = description;
        this.state = state;
        this.detail = detail;
    }

    /** The addon's id, or the jar's file name if its addon.json cannot be read. */
    String id() {
        return description == null ? jar.getFileName().toString() : description.id();
    }

    Path jar() {
        return jar;
    }

    @Nullable AddonDescription description() {
        return description;
    }

    AddonState state() {
        return state;
    }

    String detail() {
        return detail;
    }

    void state(AddonState newState, String newDetail) {
        state = newState;
        detail = newDetail;
    }

    /** True while its code may run: after {@code onLoad} started and until it is disabled. */
    boolean running() {
        return state == AddonState.LOADED || state == AddonState.ENABLED;
    }

    @Nullable AddonClassLoader loader() {
        return loader;
    }

    @Nullable LobbyAddon instance() {
        return instance;
    }

    @Nullable AddonContextImpl context() {
        return context;
    }

    void started(AddonClassLoader newLoader, LobbyAddon newInstance, AddonContextImpl newContext) {
        loader = newLoader;
        instance = newInstance;
        context = newContext;
    }

    /** Forgets the code, so the jar can be closed and the classes unloaded. */
    void stopped() {
        loader = null;
        instance = null;
        context = null;
    }

    /** What {@code /setup-lobby addons} and the doctor show. */
    AddonInfo info() {
        return new AddonInfo(id(), description == null ? id() : description.name(),
                description == null ? "?" : description.version(),
                description == null ? List.of() : description.authors(), state, detail, jar.getFileName().toString());
    }
}
