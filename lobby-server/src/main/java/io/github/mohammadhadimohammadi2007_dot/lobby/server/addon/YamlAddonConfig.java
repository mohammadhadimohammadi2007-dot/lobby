package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import io.github.mohammadhadimohammadi2007_dot.lobby.api.config.AddonConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An addon's YAML file. Values are read from the raw node values, so reading never writes defaults into
 * the tree (and never turns a section into a plain value).
 */
final class YamlAddonConfig implements AddonConfig {

    private static final int INDENT = 2;

    private final Path file;
    private final YamlConfigurationLoader loader;
    private ConfigurationNode root;

    private YamlAddonConfig(Path file) {
        this.file = file;
        this.loader = YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(INDENT)
                .build();
    }

    /**
     * Opens {@code file}, first copying the resource of the same name from the addon's jar if the file does not
     * exist yet.
     */
    static YamlAddonConfig open(Path file, ClassLoader jar) throws IOException {
        if (!Files.exists(file)) {
            try (InputStream defaults = jar.getResourceAsStream(file.getFileName().toString())) {
                if (defaults != null) {
                    Files.createDirectories(file.getParent());
                    Files.copy(defaults, file);
                }
            }
        }
        YamlAddonConfig config = new YamlAddonConfig(file);
        config.reload();
        return config;
    }

    @Override
    public synchronized void reload() throws IOException {
        try {
            root = loader.load();
        } catch (ConfigurateException e) {
            throw new IOException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save() throws IOException {
        try {
            Files.createDirectories(file.getParent());
            loader.save(root);
        } catch (ConfigurateException e) {
            throw new IOException("Cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    private ConfigurationNode node(String path) {
        if (path.isEmpty()) {
            return root;
        }
        return root.node((Object[]) path.split("\\."));
    }

    /** The scalar value at {@code path}, or {@code null} for nothing, a section or a list. */
    private synchronized @Nullable Object scalar(String path) {
        Object raw = node(path).raw();
        return raw instanceof Map<?, ?> || raw instanceof Collection<?> ? null : raw;
    }

    @Override
    public @Nullable String getString(String path, @Nullable String def) {
        Object value = scalar(path);
        return value == null ? def : String.valueOf(value);
    }

    @Override
    public int getInt(String path, int def) {
        Object value = scalar(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? def : Integer.parseInt(value.toString().strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public long getLong(String path, long def) {
        Object value = scalar(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? def : Long.parseLong(value.toString().strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public double getDouble(String path, double def) {
        Object value = scalar(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? def : Double.parseDouble(value.toString().strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object value = scalar(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null && (value.toString().equalsIgnoreCase("true") || value.toString().equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(value.toString());
        }
        return def;
    }

    @Override
    public synchronized List<String> getStringList(String path) {
        List<String> values = new ArrayList<>();
        if (node(path).raw() instanceof Collection<?> list) {
            for (Object item : list) {
                if (item != null && !(item instanceof Map<?, ?>) && !(item instanceof Collection<?>)) {
                    values.add(String.valueOf(item));
                }
            }
        }
        return values;
    }

    @Override
    public synchronized Set<String> keys(String path) {
        Set<String> keys = new LinkedHashSet<>();
        for (Object key : node(path).childrenMap().keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    @Override
    public synchronized boolean contains(String path) {
        ConfigurationNode node = node(path);
        return !node.virtual() && node.raw() != null;
    }

    @Override
    public synchronized void set(String path, @Nullable Object value) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        try {
            node(path).raw(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot store " + value + " at " + path + ": " + e.getMessage(), e);
        }
    }
}
