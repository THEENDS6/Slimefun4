package cc.theends6.sfx.api.addon;

import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;


public interface SfxScheduler {
    SfxOwnedTask runGlobal(Runnable task);

    SfxOwnedTask runGlobalLater(long delayTicks, Runnable task);

    SfxOwnedTask runGlobalRepeating(long initialDelayTicks, long periodTicks, Runnable task);

    SfxOwnedTask runRegion(Location location, Runnable task);

    SfxOwnedTask runRegionLater(Location location, long delayTicks, Runnable task);

    SfxOwnedTask runRegionRepeating(Location location, long initialDelayTicks, long periodTicks, Runnable task);

    SfxOwnedTask runEntity(Entity entity, Runnable task);

    SfxOwnedTask runAsync(Runnable task);

    SfxOwnedTask runAsyncRepeating(Duration initialDelay, Duration period, Runnable task);
}
