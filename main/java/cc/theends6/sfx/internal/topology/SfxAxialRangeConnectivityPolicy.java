package cc.theends6.sfx.internal.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SfxAxialRangeConnectivityPolicy implements SfxConnectivityPolicy {
    @Override
    public Collection<SfxNodeKey> candidateKeys(SfxTopologyNode node) {
        if (node == null) {
            return List.of();
        }
        List<SfxNodeKey> keys = new ArrayList<>();
        int range = Math.max(1, node.range());
        for (int distance = 1; distance <= range; distance++) {
            keys.add(node.key().offset(distance, 0, 0));
            keys.add(node.key().offset(-distance, 0, 0));
            keys.add(node.key().offset(0, distance, 0));
            keys.add(node.key().offset(0, -distance, 0));
            keys.add(node.key().offset(0, 0, distance));
            keys.add(node.key().offset(0, 0, -distance));
        }
        return keys;
    }
}
