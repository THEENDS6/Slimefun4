package cc.theends6.sfx.internal.cargo;

import java.util.List;
import java.util.UUID;

record SfxCargoRuntimeNetwork(
        UUID componentId,
        long topologyRevision,
        long cargoStateRevision,
        long containerRegistryRevision,
        UUID managerId,
        List<SfxCargoNodeRef> inputs,
        List<SfxCargoNodeRef> outputs
) {
}
