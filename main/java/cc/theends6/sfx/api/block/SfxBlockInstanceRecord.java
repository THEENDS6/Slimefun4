package cc.theends6.sfx.api.block;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

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
        long updatedAt,
        int energyPriorityDistance
) {
    public static final int DEFAULT_ENERGY_PRIORITY_DISTANCE = Integer.MAX_VALUE;

    public SfxBlockInstanceRecord {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(anchorKey, "anchorKey");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        stateBlob = stateBlob == null ? new byte[0] : Arrays.copyOf(stateBlob, stateBlob.length);
        energyPriorityDistance = Math.max(0, energyPriorityDistance);
    }

    @Override
    public byte[] stateBlob() {
        return Arrays.copyOf(stateBlob, stateBlob.length);
    }

    public SfxBlockInstanceRecord withState(byte[] newStateBlob, SfxBlockLifecycleState newLifecycleState, long now) {
        return new SfxBlockInstanceRecord(instanceId, typeId, anchorKey, newLifecycleState, version, ownerId, newStateBlob, now, energyPriorityDistance);
    }

    public SfxBlockInstanceRecord withState(byte[] newStateBlob, SfxBlockLifecycleState newLifecycleState,
                                            int newVersion, long now) {
        return new SfxBlockInstanceRecord(instanceId, typeId, anchorKey, newLifecycleState,
                newVersion, ownerId, newStateBlob, now, energyPriorityDistance);
    }

    public SfxBlockInstanceRecord withEnergyPriorityDistance(int newEnergyPriorityDistance, long now) {
        return new SfxBlockInstanceRecord(instanceId, typeId, anchorKey, lifecycleState, version, ownerId, stateBlob, now, newEnergyPriorityDistance);
    }
}
