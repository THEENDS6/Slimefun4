package cc.theends6.sfx.internal.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SfxNetworkScheduler {
    private final Map<UUID, SfxNetworkRuntime> runtimes = new ConcurrentHashMap<>();

    public void put(SfxNetworkRuntime runtime) {
        if (runtime != null && runtime.snapshot() != null) {
            runtimes.put(runtime.snapshot().componentId(), runtime);
        }
    }

    public void remove(UUID componentId) {
        runtimes.remove(componentId);
    }

    public void tickAll(long tick) {
        long now = System.currentTimeMillis();
        for (SfxNetworkRuntime runtime : runtimes.values()) {
            SfxNetworkReadiness readiness = runtime.readiness();
            if (readiness.ready()) {
                runtime.tick(new SfxNetworkTickContext(tick, now, readiness));
            }
        }
    }
}
