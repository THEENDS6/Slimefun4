package cc.theends6.sfx.internal.persistence;

import org.bukkit.World;

/**
 * Shared lifecycle hooks for SFX services that keep hot in-memory state and
 * persist dirty batches asynchronously. Runtime operations should mark dirty
 * state only; disk writes are driven by autosave, chunk unload and shutdown.
 */
public interface SfxDirtyPersistenceService {
    void requestDirtyFlushAsync();

    void requestChunkFlushAsync(World world, int chunkX, int chunkZ);

    void flushAllBlocking();

    void shutdown();
}
