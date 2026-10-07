package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.instance.InstanceContainer;

/**
 * The loaded lobby map.
 *
 * <p>Phase 3 will create several lobbies that share this map through
 * {@code InstanceManager#createSharedInstance(InstanceContainer)}; every lobby reads the same chunks.
 *
 * @param instance       the instance that holds the map's chunks
 * @param name           short name for logs, usually the file or folder name
 * @param format         what the map was loaded from
 * @param chunkCount     chunks kept in memory
 * @param loadMillis     how long loading took
 * @param memoryMb       rough heap memory used by the map
 * @param lightComputed  true if lighting was computed at load because the map had none stored
 */
public record LobbyWorld(
        InstanceContainer instance,
        String name,
        WorldFormat format,
        int chunkCount,
        long loadMillis,
        long memoryMb,
        boolean lightComputed
) {
}
