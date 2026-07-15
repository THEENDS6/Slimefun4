package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;

import cc.theends6.sfx.api.block.*;

import java.util.Objects;
import java.util.UUID;

public record SfxAnchorRecord(
        SfxBlockAnchorKey key,
        String materialKey,
        UUID instanceId,
        SfxBlockAnchorKind anchorKind,
        SfxBlockIntegrityState integrityState,
        long updatedAt
) {
    public SfxAnchorRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(materialKey, "materialKey");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(anchorKind, "anchorKind");
        Objects.requireNonNull(integrityState, "integrityState");
    }

    public SfxAnchorRecord touch(long now) {
        return new SfxAnchorRecord(key, materialKey, instanceId, anchorKind, integrityState, now);
    }

    public SfxAnchorRecord withIntegrity(SfxBlockIntegrityState state, long now) {
        return new SfxAnchorRecord(key, materialKey, instanceId, anchorKind, state, now);
    }
}
