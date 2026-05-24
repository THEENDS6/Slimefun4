package cc.theends6.sfx.internal.network;

import java.util.List;
import java.util.UUID;

public record SfxNetworkSnapshot(UUID componentId, SfxNetworkDomain domain, List<UUID> members, long topologyRevision) {
    public SfxNetworkSnapshot {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
