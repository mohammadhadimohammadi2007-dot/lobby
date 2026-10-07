package io.github.mohammadhadimohammadi2007_dot.lobby.server.config;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the default config files and loads YAML from disk or from the jar. */
public final class ConfigFiles {

    /** Folder inside the jar that holds the default files. */
    private static final String DEFAULTS_FOLDER = "/defaults/";

    private ConfigFiles() {
    }

    /**
     * Copies the bundled default of {@code fileName} into {@code dataDir} if the file does not exist yet.
     * An existing file is never touched.
     *
     * @return true if the file was created
     */
    public static boolean createIfMissing(Path dataDir, String fileName) throws IOException {
        Path target = dataDir.resolve(fileName);
        if (Files.exists(target)) {
            return false;
        }
        Files.createDirectories(dataDir);
        try (InputStream in = openBundled(fileName)) {
            Files.copy(in, target);
        }
        return true;
    }

    /** Loads a YAML file from disk. */
    public static ConfigurationNode loadFile(Path file) throws ConfigException {
        try {
            return YamlConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException e) {
            throw new ConfigException(file.getFileName() + " is not valid YAML: " + e.getMessage()
                    + ". Check indentation (use spaces, not tabs) and quotes.", e);
        }
    }

    /** Loads the default version of a file from inside the jar. */
    public static ConfigurationNode loadBundled(String fileName) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(openBundled(fileName), StandardCharsets.UTF_8)))
                    .build()
                    .load();
        } catch (ConfigurateException e) {
            throw new IllegalStateException("Bundled " + fileName + " is broken", e);
        }
    }

    private static InputStream openBundled(String fileName) throws IOException {
        InputStream in = ConfigFiles.class.getResourceAsStream(DEFAULTS_FOLDER + fileName);
        if (in == null) {
            throw new IOException("Bundled default " + fileName + " is missing from the jar");
        }
        return in;
    }
}
