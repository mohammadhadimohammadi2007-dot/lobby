package io.github.mohammadhadimohammadi2007_dot.lobby.server.data;

import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A data file written by commands, such as {@code data/npcs.yml}: one named entry per NPC, hologram or
 * portal, in readable YAML that admins may also edit by hand.
 *
 * <ul>
 *   <li>A broken entry is reported with its name and skipped; the rest loads. It is written back unchanged
 *       on the next save, so a command never deletes something an admin is still fixing.</li>
 *   <li>A file that is not valid YAML at all is copied to {@code <name>.broken-<time>.yml} before anything
 *       new is written, so no hand-made data is ever lost.</li>
 *   <li>Saving never blocks: the text is built right away, written by a background thread to a temporary
 *       file and then moved over the old one. Saves in quick succession are combined.</li>
 * </ul>
 */
public final class YamlDataStore<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(YamlDataStore.class);
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int INDENT = 2;

    private final Path file;
    private final String header;
    private final DataCodec<T> codec;
    private final ExecutorService writer;
    private final AtomicReference<String> pending = new AtomicReference<>();
    private Map<String, ConfigurationNode> broken = Map.of();

    /**
     * @param file   the data file, e.g. {@code <data folder>/data/npcs.yml}
     * @param header comment written at the top of the file (without {@code #})
     */
    public YamlDataStore(Path file, String header, DataCodec<T> codec) {
        this.file = file;
        this.header = header;
        this.codec = codec;
        String threadName = "lobby-data-" + file.getFileName();
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    public Path file() {
        return file;
    }

    /**
     * Reads every entry, in file order. A missing file means no entries. Blocking: call it at startup or on
     * {@code /lobby reload}, never from a player event.
     */
    public synchronized Map<String, T> load() {
        Map<String, T> entries = new LinkedHashMap<>();
        Map<String, ConfigurationNode> brokenEntries = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            broken = Map.of();
            return entries;
        }
        ConfigurationNode root;
        try {
            root = loader(null).load();
        } catch (ConfigurateException e) {
            Path backup = backup();
            LOGGER.error("{} is not valid YAML ({}). Nothing from it is loaded. It was copied to {} so it is not lost;"
                    + " fix the copy and put it back.", name(), e.getMessage(), backup);
            broken = Map.of();
            return entries;
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> child : root.childrenMap().entrySet()) {
            String key = String.valueOf(child.getKey());
            try {
                entries.put(key, codec.read(child.getValue()));
            } catch (DataException | RuntimeException e) {
                brokenEntries.put(key, child.getValue().copy());
                LOGGER.error("{}: entry '{}' is broken: {}. It is skipped (and kept in the file); the rest is loaded.",
                        name(), key, e.getMessage());
            }
        }
        broken = Collections.unmodifiableMap(brokenEntries);
        return entries;
    }

    /** Names of entries that could not be read on the last load. */
    public synchronized Set<String> brokenEntries() {
        return broken.keySet();
    }

    /**
     * Saves every entry (replacing the file). The YAML is built now; the file is written in the background.
     * Broken entries from the last load are kept unless an entry with the same name replaces them.
     */
    public void save(Map<String, T> entries) {
        String text;
        try {
            text = render(entries);
        } catch (ConfigurateException e) {
            LOGGER.error("Could not save {}: {}", name(), e.getMessage(), e);
            return;
        }
        if (pending.getAndSet(text) == null) {
            writer.execute(this::writePending);
        }
    }

    /** Waits until every save so far is on disk. For shutdown and tests. */
    public void flush() {
        try {
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Waiting for {} to be written failed: {}", name(), e.getMessage());
        }
    }

    /** Writes what is still waiting and stops the writer thread. */
    public void close() {
        flush();
        writer.shutdown();
    }

    private synchronized String render(Map<String, T> entries) throws ConfigurateException {
        StringWriter out = new StringWriter();
        YamlConfigurationLoader loader = loader(out);
        ConfigurationNode root = loader.createNode();
        for (Map.Entry<String, ConfigurationNode> entry : broken.entrySet()) {
            if (!entries.containsKey(entry.getKey())) {
                root.node(entry.getKey()).from(entry.getValue());
            }
        }
        for (Map.Entry<String, T> entry : entries.entrySet()) {
            codec.write(entry.getValue(), root.node(entry.getKey()));
        }
        loader.save(root);
        return out.toString();
    }

    private void writePending() {
        String text = pending.getAndSet(null);
        if (text == null) {
            return;
        }
        try {
            AtomicFiles.writeString(file, text);
        } catch (IOException e) {
            LOGGER.error("Could not write {}: {}. The change is only in memory until the next save.", name(), e.getMessage());
        }
    }

    private Path backup() {
        Path target = file.resolveSibling(stripExtension(file.getFileName().toString())
                + ".broken-" + LocalDateTime.now().format(BACKUP_TIME) + ".yml");
        try {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Could not back up {}: {}", name(), e.getMessage());
        }
        return target;
    }

    private YamlConfigurationLoader loader(StringWriter sink) {
        YamlConfigurationLoader.Builder builder = YamlConfigurationLoader.builder()
                .nodeStyle(NodeStyle.BLOCK)
                .indent(INDENT)
                .headerMode(HeaderMode.PRESET)
                .defaultOptions(options -> options.header(header));
        return sink == null ? builder.path(file).build() : builder.sink(() -> new BufferedWriter(sink)).build();
    }

    private String name() {
        Path parent = file.getParent();
        return parent == null ? file.getFileName().toString() : parent.getFileName() + "/" + file.getFileName();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
