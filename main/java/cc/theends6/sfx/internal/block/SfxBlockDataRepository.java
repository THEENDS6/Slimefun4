package cc.theends6.sfx.internal.block;

import java.util.UUID;

public interface SfxBlockDataRepository {
    void initialize() throws Exception;

    SfxBlockDataSnapshot loadAll() throws Exception;

    void upsertAnchor(SfxAnchorRecord anchor) throws Exception;

    void deleteAnchor(SfxBlockAnchorKey key) throws Exception;

    void upsertInstance(SfxBlockInstanceRecord instance) throws Exception;

    void deleteInstance(UUID instanceId) throws Exception;

    void close();
}
