package cc.theends6.sfx.internal.core;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Objects;
import org.bukkit.Location;

public final class SfxRegionExecutor {
    private final SfxRuntime runtime;

    public SfxRegionExecutor(SfxRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public SfxRegionTaskStatus runIfOwned(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return SfxRegionTaskStatus.WORLD_UNLOADED;
        }
        if (!runtime.isOwnedByCurrentRegion(location)) {
            return SfxRegionTaskStatus.BUSY_WRONG_REGION;
        }
        try {
            task.run();
            return SfxRegionTaskStatus.READY;
        } catch (Throwable throwable) {
            return SfxRegionTaskStatus.FAILED;
        }
    }

    public SfxRegionTaskStatus schedule(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return SfxRegionTaskStatus.WORLD_UNLOADED;
        }
        runtime.executeAt(location, task);
        return SfxRegionTaskStatus.SCHEDULED;
    }
}
