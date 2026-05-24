package cc.theends6.sfx.internal.topology;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SfxTopologyIndex {
    private final Map<SfxNodeKey, SfxTopologyNode> nodes = new ConcurrentHashMap<>();

    public void put(SfxTopologyNode node) {
        if (node != null) {
            nodes.put(node.key(), node);
        }
    }

    public Optional<SfxTopologyNode> get(SfxNodeKey key) {
        return Optional.ofNullable(nodes.get(key));
    }

    public void remove(SfxNodeKey key) {
        nodes.remove(key);
    }

    public Collection<SfxTopologyNode> nodes() {
        return java.util.Collections.unmodifiableCollection(nodes.values());
    }
}
