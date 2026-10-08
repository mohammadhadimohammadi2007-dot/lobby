package io.github.mohammadhadimohammadi2007_dot.lobby.server.data;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/** Reads and writes one entry (one NPC, hologram, portal...) of a data file. */
public interface DataCodec<T> {

    /**
     * Reads the entry stored in {@code node}.
     *
     * @throws DataException with a plain explanation if the entry is broken
     */
    T read(ConfigurationNode node) throws DataException;

    /** Writes {@code value} into the empty {@code node}. */
    void write(T value, ConfigurationNode node) throws SerializationException;
}
