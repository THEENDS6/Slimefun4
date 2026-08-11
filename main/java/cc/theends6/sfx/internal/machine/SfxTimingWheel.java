package cc.theends6.sfx.internal.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;








public final class SfxTimingWheel<K> {
    private static final int DEFAULT_SIZE = 256;

    private final List<Set<Entry<K>>> buckets;
    private final Map<K, Entry<K>> scheduled = new HashMap<>();
    private long generation;

    public SfxTimingWheel() {
        this(DEFAULT_SIZE);
    }

    public SfxTimingWheel(int size) {
        int safeSize = Math.max(32, size);
        buckets = new ArrayList<>(safeSize);
        for (int index = 0; index < safeSize; index++) {
            buckets.add(new HashSet<>());
        }
    }

    public synchronized void schedule(K key, long dueTick) {
        if (key == null) {
            return;
        }
        long safeDueTick = Math.max(0L, dueTick);
        Entry<K> existing = scheduled.get(key);
        if (existing != null && existing.dueTick() <= safeDueTick) {
            return;
        }
        Entry<K> entry = new Entry<>(key, safeDueTick, ++generation);
        scheduled.put(key, entry);
        bucket(safeDueTick).add(entry);
    }

    public synchronized void wake(K key, long currentTick) {
        schedule(key, currentTick + 1L);
    }

    
    public synchronized void reschedule(K key, long dueTick) {
        if (key == null) {
            return;
        }
        scheduled.remove(key);
        schedule(key, dueTick);
    }

    public synchronized void cancel(K key) {
        if (key != null) {
            scheduled.remove(key);
        }
    }

    public synchronized List<K> poll(long currentTick) {
        Set<Entry<K>> currentBucket = bucket(currentTick);
        if (currentBucket.isEmpty()) {
            return List.of();
        }
        List<K> due = new ArrayList<>();
        for (var iterator = currentBucket.iterator(); iterator.hasNext();) {
            Entry<K> entry = iterator.next();
            Entry<K> current = scheduled.get(entry.key());
            if (current != entry) {
                iterator.remove();
                continue;
            }
            if (entry.dueTick() > currentTick) {
                continue;
            }
            iterator.remove();
            scheduled.remove(entry.key(), entry);
            due.add(entry.key());
        }
        return due;
    }

    public synchronized boolean isScheduled(K key) {
        return key != null && scheduled.containsKey(key);
    }

    public synchronized int size() {
        return scheduled.size();
    }

    public synchronized void clear() {
        scheduled.clear();
        for (Set<Entry<K>> bucket : buckets) {
            bucket.clear();
        }
    }

    private Set<Entry<K>> bucket(long tick) {
        int index = (int) Math.floorMod(tick, (long) buckets.size());
        return buckets.get(index);
    }

    private record Entry<K>(K key, long dueTick, long generation) {
    }
}
