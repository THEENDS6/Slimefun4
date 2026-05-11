package cc.theends6.sfx.internal.block;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record SfxBlockInstanceRecord(
        UUID instanceId,
        String typeId,
        SfxBlockAnchorKey anchorKey,
        SfxBlockLifecycleState lifecycleState,
        int version,
        UUID ownerId,
        byte[] stateBlob,
        long updatedAt
) {
    public SfxBlockInstanceRecord {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(anchorKey, "anchorKey");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        stateBlob = stateBlob == null ? new byte[0] : Arrays.copyOf(stateBlob, stateBlob.length);
    }

    @Override
    public byte[] stateBlob() {
        return Arrays.copyOf(stateBlob, stateBlob.length);
    }

    public SfxBlockInstanceRecord withState(byte[] newStateBlob, SfxBlockLifecycleState newLifecycleState, long now) {
        return new SfxBlockInstanceRecord(instanceId, typeId, anchorKey, newLifecycleState, version, ownerId, newStateBlob, now);
    }
}
