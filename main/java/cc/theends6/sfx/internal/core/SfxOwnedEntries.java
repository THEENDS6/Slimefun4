package cc.theends6.sfx.internal.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class SfxOwnedEntries<T> {
    public static final String CORE_OWNER = "sfx:core";

    private final List<Entry<T>> entries = new ArrayList<>();

    public synchronized void add(String owner, T value) {
        entries.add(new Entry<>(requireOwner(owner), Objects.requireNonNull(value, "value")));
    }

    public synchronized List<T> values() {
        return entries.stream().map(Entry::value).toList();
    }

    public synchronized void removeOwner(String owner) {
        String normalized = requireOwner(owner);
        entries.removeIf(entry -> entry.owner().equals(normalized));
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized List<Entry<T>> snapshot() {
        return List.copyOf(entries);
    }

    public synchronized void restore(List<Entry<T>> snapshot) {
        entries.clear();
        if (snapshot != null) {
            entries.addAll(snapshot);
        }
    }

    public synchronized boolean anyMatch(java.util.function.Predicate<T> predicate) {
        return entries.stream().map(Entry::value).anyMatch(predicate);
    }

    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Registration owner must not be blank");
        }
        return owner.trim();
    }

    public record Entry<T>(String owner, T value) {
        public Entry {
            requireOwner(owner);
            Objects.requireNonNull(value, "value");
        }
    }
}
