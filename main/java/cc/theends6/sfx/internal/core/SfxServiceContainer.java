package cc.theends6.sfx.internal.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SfxServiceContainer {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> T put(Class<T> type, T service) {
        services.put(type, service);
        return service;
    }

    public <T> Optional<T> find(Class<T> type) {
        Object value = services.get(type);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Missing SFX service: " + type.getName()));
    }
}
