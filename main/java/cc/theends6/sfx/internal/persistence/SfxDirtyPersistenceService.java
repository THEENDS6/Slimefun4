package cc.theends6.sfx.internal.persistence;

import org.bukkit.World;






public interface SfxDirtyPersistenceService {
    void requestDirtyFlushAsync();

    void requestChunkFlushAsync(World world, int chunkX, int chunkZ);

    void flushAllBlocking();

    void shutdown();
}
