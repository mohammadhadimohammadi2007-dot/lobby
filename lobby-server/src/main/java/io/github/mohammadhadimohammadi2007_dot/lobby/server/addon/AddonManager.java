package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.ApiVersion;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.LobbyAddon;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds, loads, enables and disables the addons in the {@code addons/} folder.
 *
 * <p>Every call into an addon's code is guarded: an exception (or a missing class) stops that addon, and the
 * addons that depend on it, with an error in the log, and the lobby goes on. {@code onLoad},
 * {@code onEnable} and {@code onDisable} run on the main thread at startup and shutdown, and on a background
 * thread for {@code /setup-lobby addons reload}; never on the tick thread.
 */
public final class AddonManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddonManager.class);

    /** What {@link #reload} did, for the admin who asked. */
    public record ReloadResult(boolean ok, String message) {
    }

    private final Path folder;
    private final PlaceholderService placeholders;
    private final ClassLoader serverLoader = AddonManager.class.getClassLoader();
    /** Every addon found, by id; the loadable ones in load order first. */
    private final Map<String, Addon> addons = new LinkedHashMap<>();
    private final List<Addon> order = new ArrayList<>();
    private @Nullable AddonServices services;

    /** @param folder the addons folder, {@code <server>/addons} */
    public AddonManager(Path folder, PlaceholderService placeholders) {
        this.folder = folder;
        this.placeholders = placeholders;
    }

    /**
     * Reads every jar in the folder and runs the {@code onLoad} of those that can run, in dependency order.
     * Call before the lobby reads its config and data files.
     */
    public synchronized void loadAll() {
        List<Path> jars = jars();
        if (jars.isEmpty()) {
            return;
        }
        LOGGER.warn("Addons have full access to this server and its data, like plugins on Paper: only install"
                + " addons you trust.");
        List<AddonDescription> loadable = new ArrayList<>();
        Map<String, Path> jarOf = new LinkedHashMap<>();
        List<Addon> broken = new ArrayList<>();
        for (Path jar : jars) {
            AddonFile file = AddonFile.read(jar);
            AddonDescription description = file.description();
            if (description == null) {
                broken.add(new Addon(jar, null, AddonState.INVALID, String.valueOf(file.error())));
            } else if (jarOf.containsKey(description.id())) {
                broken.add(new Addon(jar, description, AddonState.INVALID, "another jar ("
                        + jarOf.get(description.id()).getFileName() + ") has the same id '" + description.id() + "'"));
            } else if (!ApiVersion.supports(description.apiVersion())) {
                jarOf.put(description.id(), jar);
                broken.add(new Addon(jar, description, AddonState.INCOMPATIBLE, incompatible(description)));
            } else {
                jarOf.put(description.id(), jar);
                loadable.add(description);
            }
        }
        LoadOrder sorted = LoadOrder.of(loadable);
        for (AddonDescription description : sorted.order()) {
            Addon addon = new Addon(jarOf.get(description.id()), description, AddonState.DISABLED, "");
            addons.put(description.id(), addon);
            order.add(addon);
        }
        for (AddonDescription description : loadable) {
            LoadOrder.Problem problem = sorted.problems().get(description.id());
            if (problem != null) {
                addons.put(description.id(), new Addon(jarOf.get(description.id()), description, problem.state(),
                        problem.detail()));
            }
        }
        for (Addon addon : broken) {
            addons.putIfAbsent(addon.id(), addon);
        }
        for (Addon addon : addons.values()) {
            if (addon.state().problem()) {
                LOGGER.error("Addon {} ({}) cannot start: {}", addon.id(), addon.jar().getFileName(), addon.detail());
            }
        }
        for (Addon addon : order) {
            load(addon);
        }
    }

    /** Enables every loaded addon, in order. Call once the lobby runs. */
    public synchronized void enableAll(AddonServices lobby) {
        this.services = lobby;
        for (Addon addon : List.copyOf(order)) {
            if (addon.state() == AddonState.LOADED) {
                enable(addon);
            }
        }
    }

    /** Disables every running addon, last loaded first. Call on shutdown. */
    public synchronized void disableAll() {
        for (Addon addon : order.reversed()) {
            if (addon.running()) {
                stop(addon, AddonState.DISABLED, "the server stopped");
            }
        }
    }

    /** Every addon found, sorted by id. */
    public synchronized List<AddonInfo> infos() {
        return addons.values().stream().map(Addon::info).sorted(Comparator.comparing(AddonInfo::id)).toList();
    }

    /** True if the addons folder had any jar. */
    public synchronized boolean any() {
        return !addons.isEmpty();
    }

    /**
     * Stops an addon, reads its jar again (it may have been replaced) and starts it, then reads the lobby's
     * files again so they use its new action types and placeholders. Refused when other running addons use it.
     */
    public ReloadResult reload(String id) {
        boolean reloadData;
        synchronized (this) {
            Addon old = addons.get(id);
            if (old == null) {
                return new ReloadResult(false, "There is no addon '" + id + "'.");
            }
            List<String> users = order.stream()
                    .filter(Addon::running)
                    .filter(other -> other != old && other.description() != null
                            && (other.description().depends().contains(id) || other.description().softDepends().contains(id)))
                    .map(Addon::id)
                    .toList();
            if (!users.isEmpty()) {
                return new ReloadResult(false, "Not reloaded: " + String.join(", ", users) + " use" + (users.size() == 1
                        ? "s" : "") + " it. Restart the server instead.");
            }
            reloadData = old.context() != null && old.context().registeredDataTypes();
            if (old.running()) {
                stop(old, AddonState.DISABLED, "reloading");
            }
            AddonFile file = jars().stream().map(AddonFile::read)
                    .filter(found -> found.description() != null && found.description().id().equals(id))
                    .findFirst().orElse(null);
            if (file == null || file.description() == null) {
                return new ReloadResult(false, "Stopped, but no jar in " + folder.getFileName() + "/ has the id '" + id
                        + "' any more.");
            }
            AddonDescription description = file.description();
            Addon fresh = new Addon(file.jar(), description, AddonState.DISABLED, "");
            replace(old, fresh);
            if (!ApiVersion.supports(description.apiVersion())) {
                fresh.state(AddonState.INCOMPATIBLE, incompatible(description));
                return new ReloadResult(false, "Not started: " + fresh.detail() + ".");
            }
            load(fresh);
            if (fresh.state() == AddonState.LOADED && services != null) {
                enable(fresh);
            }
            reloadData |= fresh.context() != null && fresh.context().registeredDataTypes();
            if (fresh.state() != AddonState.ENABLED) {
                return new ReloadResult(false, "Not started: " + fresh.detail() + ". The console log has the details.");
            }
        }
        if (reloadData && services != null) {
            // Outside the lock: reading the files may call into addons (action types, placeholders).
            services.reloadData().run();
        }
        return new ReloadResult(true, "Reloaded " + id + ".");
    }

    /**
     * An addon's code threw: logs it and stops the addon (and those that depend on it). The addon stops running
     * code at once; the rest of the cleanup (its onDisable, removing what it registered) happens on a virtual
     * thread, so a listener can fail in the middle of an event safely and onDisable is never on the tick thread.
     */
    void fail(Addon addon, String where, Throwable error) {
        synchronized (addon) {
            if (!addon.running()) {
                return;
            }
            addon.state(AddonState.FAILED, "failed in " + where + ": " + error);
        }
        LOGGER.error("Addon {} failed in {} and is disabled", addon.id(), where, error);
        Async.run(() -> {
            synchronized (this) {
                if (addon.context() != null) {
                    stop(addon, AddonState.FAILED, addon.detail());
                }
            }
        });
    }

    // ---------------- Internals ----------------

    private void load(Addon addon) {
        AddonDescription description = addon.description();
        List<AddonClassLoader> dependencies = new ArrayList<>();
        for (String dependency : description.depends()) {
            Addon other = addons.get(dependency);
            if (other == null || !other.running() || other.loader() == null) {
                addon.state(AddonState.MISSING_DEPENDENCY, "needs '" + dependency + "', which did not start");
                LOGGER.error("Addon {} cannot start: {}", addon.id(), addon.detail());
                return;
            }
            dependencies.add(other.loader());
        }
        for (String dependency : description.softDepends()) {
            Addon other = addons.get(dependency);
            if (other != null && other.running() && other.loader() != null) {
                dependencies.add(other.loader());
            }
        }
        AddonClassLoader loader;
        try {
            loader = new AddonClassLoader(description.id(), addon.jar(), serverLoader, dependencies);
        } catch (IOException e) {
            addon.state(AddonState.INVALID, "cannot open the jar: " + e.getMessage());
            LOGGER.error("Addon {} cannot start: {}", addon.id(), addon.detail());
            return;
        }
        LobbyAddon instance;
        try {
            Class<?> main = Class.forName(description.mainClass(), true, loader);
            if (!LobbyAddon.class.isAssignableFrom(main)) {
                close(loader);
                addon.state(AddonState.INVALID, "the main class " + description.mainClass() + " does not implement LobbyAddon");
                LOGGER.error("Addon {} cannot start: {}", addon.id(), addon.detail());
                return;
            }
            instance = (LobbyAddon) main.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            close(loader);
            addon.state(AddonState.INVALID, "cannot create the main class " + description.mainClass() + ": " + e);
            LOGGER.error("Addon {} cannot start: {}", addon.id(), addon.detail(), e);
            return;
        }
        AddonContextImpl context = new AddonContextImpl(addon, description, this, folder.resolve(description.id()),
                loader, placeholders);
        addon.started(loader, instance, context);
        addon.state(AddonState.LOADED, "");
        context.loading(true);
        try {
            instance.onLoad(context);
        } catch (Exception | LinkageError e) {
            LOGGER.error("Addon {} failed in onLoad and is disabled", addon.id(), e);
            stop(addon, AddonState.FAILED, "failed in onLoad: " + e);
        } finally {
            context.loading(false);
        }
    }

    private void enable(Addon addon) {
        for (String dependency : addon.description().depends()) {
            Addon other = addons.get(dependency);
            if (other == null || other.state() != AddonState.ENABLED) {
                stop(addon, AddonState.MISSING_DEPENDENCY, "needs '" + dependency + "', which did not start");
                LOGGER.error("Addon {} cannot start: {}", addon.id(), addon.detail());
                return;
            }
        }
        AddonContextImpl context = addon.context();
        context.enable(services);
        try {
            addon.instance().onEnable(context);
        } catch (Exception | LinkageError e) {
            LOGGER.error("Addon {} failed in onEnable and is disabled", addon.id(), e);
            stop(addon, AddonState.FAILED, "failed in onEnable: " + e);
            return;
        }
        if (addon.state() == AddonState.LOADED) {
            addon.state(AddonState.ENABLED, "");
            AddonDescription description = addon.description();
            LOGGER.info("Addon {} {} enabled{}", description.name(), description.version(),
                    description.authors().isEmpty() ? "" : " (by " + String.join(", ", description.authors()) + ")");
        }
    }

    /**
     * Stops a running addon: first the addons that depend on it, then its {@code onDisable}, then everything it
     * registered is taken back and its jar closed.
     */
    private void stop(Addon addon, AddonState state, String detail) {
        for (Addon other : order.reversed()) {
            if (other != addon && other.context() != null && other.description().depends().contains(addon.id())) {
                stop(other, state == AddonState.DISABLED ? AddonState.DISABLED : AddonState.MISSING_DEPENDENCY,
                        state == AddonState.DISABLED ? detail : "needs '" + addon.id() + "', which stopped");
            }
        }
        AddonContextImpl context = addon.context();
        LobbyAddon instance = addon.instance();
        if (context != null && instance != null) {
            try {
                instance.onDisable(context);
            } catch (Exception | LinkageError e) {
                LOGGER.error("Addon {} failed in onDisable", addon.id(), e);
            }
            context.cleanup();
        }
        AddonClassLoader loader = addon.loader();
        addon.stopped();
        addon.state(state, detail);
        if (loader != null) {
            close(loader);
        }
        if (state == AddonState.DISABLED && context != null) {
            LOGGER.info("Addon {} disabled", addon.id());
        }
    }

    private void replace(Addon old, Addon fresh) {
        addons.put(fresh.id(), fresh);
        int index = order.indexOf(old);
        if (index >= 0) {
            order.set(index, fresh);
        } else {
            order.add(fresh);
        }
    }

    /** The jars in the addons folder, by name. Creates the folder, so admins see where addons go. */
    private List<Path> jars() {
        try {
            Files.createDirectories(folder);
            try (Stream<Path> files = Files.list(folder)) {
                return files.filter(file -> Files.isRegularFile(file)
                                && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            LOGGER.error("Cannot read the addons folder {}: {}", folder, e.getMessage());
            return List.of();
        }
    }

    private static String incompatible(AddonDescription description) {
        return "written for API version " + description.apiVersion() + ", this lobby has " + ApiVersion.CURRENT;
    }

    private static void close(AddonClassLoader loader) {
        try {
            loader.close();
        } catch (IOException e) {
            LOGGER.warn("Could not close the jar of addon {}: {}", loader.addonId(), e.getMessage());
        }
    }
}
