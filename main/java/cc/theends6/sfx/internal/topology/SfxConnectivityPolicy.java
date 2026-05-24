package cc.theends6.sfx.internal.topology;

import java.util.Collection;

public interface SfxConnectivityPolicy {
    Collection<SfxNodeKey> candidateKeys(SfxTopologyNode node);

    default boolean canConnect(SfxTopologyNode a, SfxTopologyNode b) {
        return a != null && b != null && a.domain() == b.domain();
    }
}
