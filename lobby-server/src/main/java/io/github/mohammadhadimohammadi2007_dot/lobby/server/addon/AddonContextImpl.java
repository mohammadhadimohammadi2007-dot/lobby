package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonContext;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.AddonDescription;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.action.ActionTypes;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.command.AddonCommands;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.config.AddonConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.event.AddonEvents;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.hologram.Holograms;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.menu.Menus;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.npc.Npcs;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.placeholder.Placeholders;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.scheduler.AddonScheduler;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Clients;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Network;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.Permissions;
import io.github.mohammadhadimohammadi2007_dot.lobby.api.service.PlayerSettings;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.placeholder.PlaceholderService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One addon's view of the lobby. Every registration goes through here and is remembered, so
 * {@link #cleanup()} can take all of it back when the addon is disabled.
 */
final class AddonContextImpl implements AddonContext {

    private final Addon addon;
    private final AddonDescription description;
    private final AddonManager manager;
    private final Path dataFolder;
    private final ClassLoader jar;
    private final Logger logger;
    private final Map<String, YamlAddonConfig> configs = new ConcurrentHashMap<>();
    private final ApiPlaceholders placeholders;
    private final ApiActionTypes actionTypes;
    private final ApiScheduler scheduler;
    private final ApiEvents events;
    private volatile boolean loading;
    private volatile @Nullable Enabled enabled;

    /** The services that only exist once the lobby runs. */
    private record Enabled(ApiCommands commands, ApiMenus menus, Holograms holograms, Npcs npcs,
                           Permissions permissions, PlayerSettings settings, Network network, Clients clients) {
    }

    AddonContextImpl(Addon addon, AddonDescription description, AddonManager manager, Path dataFolder,
                     ClassLoader jar, PlaceholderService placeholderService) {
        this.addon = addon;
        this.description = description;
        this.manager = manager;
        this.dataFolder = dataFolder;
        this.jar = jar;
        this.logger = new PrefixedLogger(description.id(), description.name());
        this.placeholders = new ApiPlaceholders(this, placeholderService);
        this.actionTypes = new ApiActionTypes(this, placeholderService);
        this.scheduler = new ApiScheduler(this);
        this.events = new ApiEvents(this);
    }

    // ---------------- Lifecycle, used by the manager ----------------

    /** Opens or closes the registries that only accept registrations during {@code onLoad}. */
    void loading(boolean value) {
        loading = value;
    }

    boolean loading() {
        return loading;
    }

    /** Makes the services of the running lobby available, before {@code onEnable}. */
    void enable(AddonServices services) {
        enabled = new Enabled(new ApiCommands(this, services), new ApiMenus(this, services),
                new ApiHolograms(services.holograms()), new ApiNpcs(services.npcs()),
                new ApiPermissions(services.permissions()), new ApiPlayerSettings(this, services.settings()),
                new ApiNetwork(services.bridge(), services.actions()), new ApiClients(services.bridge()));
    }

    /** True while the addon may run code (its listeners, actions and placeholders check this). */
    boolean running() {
        return addon.running();
    }

    /** Reports an error of the addon's code: it is logged and the addon is disabled. */
    void fail(String where, Throwable error) {
        manager.fail(addon, where, error);
    }

    /** True if the addon registered placeholders or action types, which data files may use. */
    boolean registeredDataTypes() {
        return placeholders.count() > 0 || actionTypes.count() > 0;
    }

    /** Takes back everything the addon registered. Never throws. */
    void cleanup() {
        events.cleanup();
        scheduler.cleanup();
        Enabled services = enabled;
        if (services != null) {
            services.commands().cleanup();
            services.menus().cleanup();
        }
        placeholders.cleanup();
        actionTypes.cleanup();
        enabled = null;
    }

    // ---------------- AddonContext ----------------

    @Override
    public AddonDescription description() {
        return description;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataFolder() {
        try {
            java.nio.file.Files.createDirectories(dataFolder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + dataFolder, e);
        }
        return dataFolder;
    }

    @Override
    public AddonConfig config(String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("A config file name without folders, e.g. config.yml: " + fileName);
        }
        return configs.computeIfAbsent(fileName, name -> {
            try {
                return YamlAddonConfig.open(dataFolder.resolve(name), jar);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public Placeholders placeholders() {
        return placeholders;
    }

    @Override
    public ActionTypes actionTypes() {
        return actionTypes;
    }

    @Override
    public AddonCommands commands() {
        return services().commands();
    }

    @Override
    public AddonEvents events() {
        services();
        return events;
    }

    @Override
    public AddonScheduler scheduler() {
        return scheduler;
    }

    @Override
    public Menus menus() {
        return services().menus();
    }

    @Override
    public Holograms holograms() {
        return services().holograms();
    }

    @Override
    public Npcs npcs() {
        return services().npcs();
    }

    @Override
    public Permissions permissions() {
        return services().permissions();
    }

    @Override
    public PlayerSettings playerSettings() {
        return services().settings();
    }

    @Override
    public Network network() {
        return services().network();
    }

    @Override
    public Clients clients() {
        return services().clients();
    }

    private Enabled services() {
        Enabled services = enabled;
        if (services == null) {
            throw new IllegalStateException("Addon '" + description.id() + "': this part of the lobby is only available"
                    + " from onEnable on (and not after the addon was disabled)");
        }
        return services;
    }
}
