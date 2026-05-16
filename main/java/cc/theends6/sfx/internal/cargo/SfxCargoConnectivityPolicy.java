package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.topology.SfxTopologyConnectivityPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SfxCargoConnectivityPolicy implements SfxTopologyConnectivityPolicy {
    private final int range;

    SfxCargoConnectivityPolicy(int range) {
        this.range = Math.max(1, range);
    }

    @Override
    public Collection<SfxBlockAnchorKey> findBackboneNeighbours(SfxBlockAnchorKey origin) {
        return axialKeys(origin);
    }

    @Override
    public Collection<SfxBlockAnchorKey> findAttachableBackbones(SfxBlockAnchorKey terminal) {
        return axialKeys(terminal);
    }

    private List<SfxBlockAnchorKey> axialKeys(SfxBlockAnchorKey origin) {
        List<SfxBlockAnchorKey> keys = new ArrayList<>(range * 6);
        for (int distance = 1; distance <= range; distance++) {
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x() + distance, origin.y(), origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x() - distance, origin.y(), origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y() + distance, origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y() - distance, origin.z()));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y(), origin.z() + distance));
            keys.add(new SfxBlockAnchorKey(origin.worldId(), origin.x(), origin.y(), origin.z() - distance));
        }
        return keys;
    }
}
