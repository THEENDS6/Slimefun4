package cc.theends6.sfx.internal.topology;

import java.util.List;
import java.util.UUID;

public record SfxTopologySnapshot(UUID componentId, SfxTopologyDomain domain, List<SfxTopologyNode> nodes, long revision) {
    public SfxTopologySnapshot {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
