package io.github.mohammadhadimohammadi2007_dot.lobby.server.world;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkLoader;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Wraps another loader so chunks can be read but are never written or unloaded.
 * The lobby map on disk is never changed while the server runs.
 */
final class ReadOnlyChunkLoader implements ChunkLoader {

    private final ChunkLoader delegate;

    ReadOnlyChunkLoader(ChunkLoader delegate) {
        this.delegate = delegate;
    }

    @Override
    public void loadInstance(Instance instance) {
        delegate.loadInstance(instance);
    }

    @Override
    public @Nullable Chunk loadChunk(Instance instance, int chunkX, int chunkZ) {
        return delegate.loadChunk(instance, chunkX, chunkZ);
    }

    @Override
    public boolean supportsParallelLoading() {
        return delegate.supportsParallelLoading();
    }

    @Override
    public void saveInstance(Instance instance) {
        // Read-only.
    }

    @Override
    public void saveChunk(Chunk chunk) {
        // Read-only.
    }

    @Override
    public void saveChunks(Collection<Chunk> chunks) {
        // Read-only.
    }

    @Override
    public void unloadChunk(Chunk chunk) {
        // Read-only.
    }
}
