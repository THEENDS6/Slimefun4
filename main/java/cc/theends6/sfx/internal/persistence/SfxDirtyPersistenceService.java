package cc.theends6.sfx.internal.persistence;

import org.bukkit.World;






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
