package cc.theends6.sfx.internal.persistence;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SfxDirtyTracker<K> {
    private final Set<K> dirty = new LinkedHashSet<>();

    public synchronized void markDirty(K key) {
        if (key != null) { dirty.add(key); }
    }

    public synchronized Set<K> snapshotAndClear() {
        Set<K> snapshot = new LinkedHashSet<>(dirty);
        dirty.clear();
        return snapshot;
    }

    public synchronized void restore(Set<K> keys) {
        if (keys != null) { dirty.addAll(keys); }
    }

    public synchronized Set<K> snapshot() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(dirty));
    }
}
