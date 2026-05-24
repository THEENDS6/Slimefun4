package cc.theends6.sfx.internal.energy;

import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record EnergyRuntimeGrid(
        UUID componentId,
        long topologyRevision,
        UUID regulatorId,
        SfxBlockAnchorKey regulatorKey,
        Set<UUID> members,
        Set<UUID> controllers,
        List<SfxBlockInstanceRecord> capacitors,
        List<SfxBlockInstanceRecord> generators,
        List<SfxBlockInstanceRecord> chargers,
        List<SfxBlockInstanceRecord> electricConsumers,
        List<SfxBlockInstanceRecord> configurableConsumers,
        List<SfxBlockInstanceRecord> configurableProducers
) {
}
