package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.chat.ChatConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.display.DisplayConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.hotbar.HotbarConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.menu.MenuConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns the loaded configuration and reloads it on request.
 *
 * <p>Loading reads files, so call {@link #load()} and {@link #reload()} off the tick thread.
 * Other parts of the server read {@link #current()} or register a reload listener.
 */
public final class ConfigManager {

    public static final String CONFIG_FILE = "config.yml";
    public static final String INTEGRATIONS_FILE = "integrations.yml";
    public static final String MESSAGES_FILE = "messages.yml";
    public static final String CHAT_FILE = "chat.yml";
    public static final String MENUS_FILE = "menus.yml";
    public static final String DISPLAY_FILE = "display.yml";
    public static final String HOTBAR_FILE = "hotbar.yml";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private final Path dataDir;
    private final List<Consumer<ConfigSnapshot>> reloadListeners = new CopyOnWriteArrayList<>();
    private volatile ConfigSnapshot current;

    /** @param dataDir folder holding the config files (the server's working directory) */
    public ConfigManager(Path dataDir) {
        this.dataDir = dataDir;
    }

    /** Folder holding the config files. */
    public Path dataDir() {
        return dataDir;
    }

    /** The configuration currently in use. Only valid after {@link #load()}. */
    public ConfigSnapshot current() {
        ConfigSnapshot snapshot = current;
        if (snapshot == null) {
            throw new IllegalStateException("Configuration has not been loaded yet");
        }
        return snapshot;
    }

    /**
     * Creates missing files from the defaults, then loads everything. Logs every warning.
     *
     * @throws ConfigException if a file cannot be used at all
     */
    public ConfigSnapshot load() throws ConfigException {
        for (String file : List.of(CONFIG_FILE, INTEGRATIONS_FILE, CHAT_FILE, MENUS_FILE, DISPLAY_FILE, HOTBAR_FILE, MESSAGES_FILE)) {
            try {
                if (ConfigFiles.createIfMissing(dataDir, file)) {
                    LOGGER.info("Created default {}", file);
                }
            } catch (IOException e) {
                throw new ConfigException("Could not create " + file + " in " + dataDir.toAbsolutePath()
                        + ": " + e.getMessage(), e);
            }
        }
        ConfigSnapshot snapshot = read(dataDir);
        snapshot.warnings().forEach(LOGGER::warn);
        current = snapshot;
        return snapshot;
    }

    /**
     * Loads everything again. If anything fails, the old configuration stays active.
     *
     * @return the new configuration and the changed options that need a restart
     * @throws ConfigException if a file cannot be used; nothing was changed in that case
     */
    public ReloadResult reload() throws ConfigException {
        ConfigSnapshot before = current();
        ConfigSnapshot after = load();
        List<String> restartNeeded = RestartRequired.changedOptions(before, after);
        for (Consumer<ConfigSnapshot> listener : reloadListeners) {
            try {
                listener.accept(after);
            } catch (RuntimeException e) {
                LOGGER.error("A component failed to apply the reloaded configuration", e);
            }
        }
        return new ReloadResult(after, restartNeeded);
    }

    /** Runs {@code listener} with the new configuration after every successful reload. */
    public void onReload(Consumer<ConfigSnapshot> listener) {
        reloadListeners.add(listener);
    }

    /** Reads all three files from {@code dataDir} without changing any state. Useful for tests. */
    public static ConfigSnapshot read(Path dataDir) throws ConfigException {
        List<String> warnings = new ArrayList<>();

        ConfigReader configReader = reader(dataDir, CONFIG_FILE);
        LobbyConfig config = LobbyConfig.read(configReader);
        warnings.addAll(configReader.warnings());

        ConfigReader integrationsReader = reader(dataDir, INTEGRATIONS_FILE);
        IntegrationsConfig integrations = IntegrationsConfig.read(integrationsReader);
        warnings.addAll(integrationsReader.warnings());

        ConfigReader chatReader = reader(dataDir, CHAT_FILE);
        ChatConfig chat = ChatConfig.read(chatReader);
        warnings.addAll(chatReader.warnings());

        ConfigReader menusReader = reader(dataDir, MENUS_FILE);
        MenuConfig menus = MenuConfig.read(menusReader);
        warnings.addAll(menusReader.warnings());

        ConfigReader displayReader = reader(dataDir, DISPLAY_FILE);
        DisplayConfig display = DisplayConfig.read(displayReader);
        warnings.addAll(displayReader.warnings());

        ConfigReader hotbarReader = reader(dataDir, HOTBAR_FILE);
        HotbarConfig hotbar = HotbarConfig.read(hotbarReader);
        warnings.addAll(hotbarReader.warnings());

        ConfigReader messagesReader = reader(dataDir, MESSAGES_FILE);
        Messages messages = Messages.read(messagesReader);
        warnings.addAll(messagesReader.warnings());

        return new ConfigSnapshot(config, integrations, chat, menus, display, hotbar, messages, warnings);
    }

    private static ConfigReader reader(Path dataDir, String fileName) throws ConfigException {
        return new ConfigReader(fileName, ConfigFiles.loadFile(dataDir.resolve(fileName)), ConfigFiles.loadBundled(fileName));
    }

    /**
     * Outcome of a successful reload.
     *
     * @param snapshot      the configuration now in use
     * @param restartNeeded changed options that only apply after a restart (empty if none)
     */
    public record ReloadResult(ConfigSnapshot snapshot, List<String> restartNeeded) {
    }
}
