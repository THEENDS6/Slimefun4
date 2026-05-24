package cc.theends6.sfx.internal.persistence;

import org.bukkit.World;

/**
 * Shared lifecycle hooks for SFX services that keep hot in-memory state and
 * persist dirty batches asynchronously. Runtime operations should mark dirty
 * state only; disk writes are driven by autosave, chunk unload and shutdown.
 */
public interface SfxDirtyPersistenceService extends SfxFlushCoordinator.FlushableStore {
    @Override
    default String name() {
        return getClass().getSimpleName();
    }

    @Override
    void requestDirtyFlushAsync();

    void requestChunkFlushAsync(World world, int chunkX, int chunkZ);

    @Override
    void flushAllBlocking();

    void shutdown();
}
