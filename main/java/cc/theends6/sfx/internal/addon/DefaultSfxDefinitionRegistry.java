package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.registry.SfxDefinitionRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class DefaultSfxDefinitionRegistry<T> implements SfxDefinitionRegistry<T> {
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>();
    private final ThreadLocal<String> owner = new ThreadLocal<>();

    @Override
    public synchronized void register(String id, T definition) {
        String normalized = normalize(id);
        String activeOwner = owner.get();
        if (activeOwner == null || activeOwner.isBlank()) {
            throw new IllegalStateException("Definitions must be registered through an addon-scoped registry view");
        }
        Entry<T> previous = entries.putIfAbsent(normalized,
                new Entry<>(activeOwner, Objects.requireNonNull(definition, "definition")));
        if (previous != null) {
            throw new IllegalStateException("Duplicate definition id " + normalized
                    + " from " + activeOwner + "; already owned by " + previous.owner());
        }
    }

    @Override
    public synchronized Optional<T> find(String id) {
        Entry<T> entry = entries.get(normalize(id));
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    @Override
    public synchronized Collection<T> definitions() {
        return entries.values().stream().map(Entry::value).toList();
    }

    SfxDefinitionRegistry<T> view(String addonId) {
        return (SfxDefinitionRegistry<T>) Proxy.newProxyInstance(
                SfxDefinitionRegistry.class.getClassLoader(),
                new Class<?>[] {SfxDefinitionRegistry.class},
                (proxy, method, args) -> {
                    String previous = owner.get();
                    owner.set(addonId);
                    try {
                        return method.invoke(this, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        if (previous == null) owner.remove(); else owner.set(previous);
                    }
                });
    }

    synchronized void removeOwner(String addonId) {
        entries.entrySet().removeIf(entry -> entry.getValue().owner().equals(addonId));
    }

    synchronized void clear() {
        entries.clear();
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Definition id must not be blank");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Definition id must be a lowercase namespace:name: " + id);
        }
        return normalized;
    }

    private record Entry<T>(String owner, T value) {}
}
