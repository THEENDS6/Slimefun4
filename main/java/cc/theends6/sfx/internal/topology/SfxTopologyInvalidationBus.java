package cc.theends6.sfx.internal.topology;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SfxTopologyInvalidationBus {
    public record Event(SfxTopologyDomain domain, SfxNodeKey key, String reason) {
    }

    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<Event> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void invalidate(SfxTopologyDomain domain, SfxNodeKey key, String reason) {
        Event event = new Event(domain, key, reason == null ? "unknown" : reason);
        for (Consumer<Event> listener : listeners) {
            listener.accept(event);
        }
    }
}
