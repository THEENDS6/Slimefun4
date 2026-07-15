package cc.theends6.sfx.internal.topology;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import java.util.Collection;

public interface SfxTopologyConnectivityPolicy {
    Collection<SfxBlockAnchorKey> findBackboneNeighbours(SfxBlockAnchorKey origin);

    Collection<SfxBlockAnchorKey> findAttachableBackbones(SfxBlockAnchorKey terminal);
}
