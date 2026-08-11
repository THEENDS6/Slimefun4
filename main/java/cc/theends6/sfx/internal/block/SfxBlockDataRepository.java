package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SfxBlockDataRepository {
    void initialize() throws Exception;

    SfxBlockDataSnapshot loadAll() throws Exception;

    
    SfxBlockDataSnapshot loadIndex() throws Exception;

    
    SfxBlockDataSnapshot loadChunk(UUID worldId, int chunkX, int chunkZ) throws Exception;

    CompletableFuture<Void> persistChangesAsync(
            Collection<SfxAnchorRecord> anchors,
            Collection<SfxBlockInstanceRecord> instances,
            Collection<SfxBlockAnchorKey> anchorDeletes,
            Collection<UUID> instanceDeletes);

    void persistChanges(
            Collection<SfxAnchorRecord> anchors,
            Collection<SfxBlockInstanceRecord> instances,
            Collection<SfxBlockAnchorKey> anchorDeletes,
            Collection<UUID> instanceDeletes) throws Exception;

    void upsertAnchor(SfxAnchorRecord anchor) throws Exception;

    void deleteAnchor(SfxBlockAnchorKey key) throws Exception;

    void upsertInstance(SfxBlockInstanceRecord instance) throws Exception;

    void deleteInstance(UUID instanceId) throws Exception;

    void awaitPendingWrites() throws Exception;

    void close();
}
