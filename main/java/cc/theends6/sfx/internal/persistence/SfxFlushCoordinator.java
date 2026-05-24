package cc.theends6.sfx.internal.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SfxFlushCoordinator {
    public interface FlushableStore {
        String name();
        void requestDirtyFlushAsync();
        void flushAllBlocking();
    }

    private final Logger logger;
    private final List<FlushableStore> stores = new ArrayList<>();

    public SfxFlushCoordinator(Logger logger) {
        this.logger = logger;
    }

    public void register(FlushableStore store) {
        if (store != null) { stores.add(store); }
    }

    public void requestAsyncFlushAll() {
        for (FlushableStore store : stores) {
            store.requestDirtyFlushAsync();
        }
    }

    public void flushAllBlocking() {
        for (FlushableStore store : stores) {
            try {
                store.flushAllBlocking();
            } catch (Throwable throwable) {
                if (logger != null) {
                    logger.warning("Failed to flush " + store.name() + ": " + throwable.getMessage());
                }
            }
        }
    }
}
