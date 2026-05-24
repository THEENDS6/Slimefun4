package cc.theends6.sfx.internal.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;


public final class SfxNetworkExecution {
    private static final AtomicLong TICK_COUNTER = new AtomicLong();

    private SfxNetworkExecution() {}

    public static SfxNetworkSnapshot snapshot(UUID componentId, SfxNetworkDomain domain, Collection<UUID> members, long topologyRevision) {
        return new SfxNetworkSnapshot(componentId, domain, members == null ? java.util.List.of() : new ArrayList<>(members), topologyRevision);
    }

    public static void tick(SfxNetworkSnapshot snapshot, SfxNetworkReadiness readiness, Runnable action) {
        if (snapshot == null || action == null) {
            return;
        }
        SfxNetworkTickContext context = new SfxNetworkTickContext(TICK_COUNTER.incrementAndGet(), System.currentTimeMillis(), readiness == null ? SfxNetworkReadiness.READY : readiness);
        if (!context.readiness().ready()) {
            return;
        }
        action.run();
    }
}
