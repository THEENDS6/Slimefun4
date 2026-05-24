package cc.theends6.sfx.internal.topology;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SfxTopologyNode(
        UUID instanceId,
        SfxNodeKey key,
        SfxTopologyDomain domain,
        Set<SfxNodeCapability> capabilities,
        UUID ownerId,
        int channel,
        int range
) {
    public SfxTopologyNode {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(domain, "domain");
        capabilities = capabilities == null || capabilities.isEmpty() ? EnumSet.noneOf(SfxNodeCapability.class) : EnumSet.copyOf(capabilities);
        range = Math.max(1, range);
    }

    public boolean has(SfxNodeCapability capability) {
        return capabilities.contains(capability);
    }
}
