package io.github.mohammadhadimohammadi2007_dot.lobby.api.config;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * A YAML file in the addon's folder. Paths use dots: {@code rewards.daily.amount}. Reading never changes the
 * file; a missing or wrong value gives the default.
 *
 * <p>Reads are from memory. {@link #reload()} and {@link #save()} touch the disk, so call them off the tick
 * thread.
 */
public interface AddonConfig {

    /** The text at {@code path}, or {@code def}. Numbers and booleans are returned as text. */
    @Nullable String getString(String path, @Nullable String def);

    int getInt(String path, int def);

    long getLong(String path, long def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    /** A list of texts, or an empty list. */
    List<String> getStringList(String path);

    /** The keys directly below {@code path} ({@code ""} for the top level), in file order. */
    Set<String> keys(String path);

    /** True if something is set at {@code path}. */
    boolean contains(String path);

    /** Sets a value in memory: text, number, boolean, a list of those, or {@code null} to remove it. */
    void set(String path, @Nullable Object value);

    /** Reads the file again. */
    void reload() throws IOException;

    /** Writes the values in memory to the file. Comments of the file are not kept. */
    void save() throws IOException;
}
