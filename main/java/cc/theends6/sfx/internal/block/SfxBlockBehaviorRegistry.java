package cc.theends6.sfx.internal.block;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SfxBlockBehaviorRegistry {
    private final Map<String, SfxBlockBehavior> behaviors = new ConcurrentHashMap<>();

    public void register(SfxBlockBehavior behavior) {
        if (behavior != null && behavior.typeId() != null) {
            behaviors.put(behavior.typeId(), behavior);
        }
    }

    public Optional<SfxBlockBehavior> find(String typeId) {
        return Optional.ofNullable(behaviors.get(typeId));
    }

    public Collection<SfxBlockBehavior> behaviors() {
        return java.util.Collections.unmodifiableCollection(behaviors.values());
    }
}
