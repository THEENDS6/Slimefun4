package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.addon.SfxAddonResources;
import cc.theends6.sfx.api.addon.SfxOwnedTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;


final class SfxAddonResourceRegistry {
    private final JavaPlugin plugin;
    private final Map<String, Deque<AutoCloseable>> resources = new LinkedHashMap<>();

    SfxAddonResourceRegistry(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    SfxAddonResources view(String owner) {
        return new View(owner);
    }

    synchronized void unregisterAll(String owner) {
        Deque<AutoCloseable> owned = resources.remove(owner);
        if (owned == null) return;
        while (!owned.isEmpty()) {
            try {
                owned.removeLast().close();
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to close addon resource for " + owner + ": " + exception.getMessage());
            }
        }
    }

    synchronized void clear() {
        for (String owner : java.util.List.copyOf(resources.keySet())) {
            unregisterAll(owner);
        }
    }

    private synchronized <T extends AutoCloseable> T track(String owner, T resource) {
        resources.computeIfAbsent(owner, ignored -> new ArrayDeque<>()).addLast(resource);
        return resource;
    }

    private SfxOwnedTask task(String owner, ScheduledTask task) {
        return track(owner, new TaskHandle(task));
    }

    private final class View implements SfxAddonResources {
        private final String owner;

        private View(String owner) {
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner must not be blank");
            this.owner = owner;
        }

        @Override
        public <T extends Listener> T registerListener(T listener) {
            Objects.requireNonNull(listener, "listener");
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            track(owner, () -> HandlerList.unregisterAll(listener));
            return listener;
        }

        @Override public SfxOwnedTask runGlobal(Runnable action) {
            return task(owner, plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> action.run()));
        }
        @Override public SfxOwnedTask runGlobalLater(long delay, Runnable action) {
            return task(owner, plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, ignored -> action.run(), Math.max(1L, delay)));
        }
        @Override public SfxOwnedTask runGlobalRepeating(long delay, long period, Runnable action) {
            return task(owner, plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> action.run(), Math.max(1L, delay), Math.max(1L, period)));
        }
        @Override public SfxOwnedTask runRegion(Location location, Runnable action) {
            return task(owner, plugin.getServer().getRegionScheduler().run(plugin, location, ignored -> action.run()));
        }
        @Override public SfxOwnedTask runRegionLater(Location location, long delay, Runnable action) {
            return task(owner, plugin.getServer().getRegionScheduler().runDelayed(plugin, location, ignored -> action.run(), Math.max(1L, delay)));
        }
        @Override public SfxOwnedTask runRegionRepeating(Location location, long delay, long period, Runnable action) {
            return task(owner, plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, location, ignored -> action.run(), Math.max(1L, delay), Math.max(1L, period)));
        }
        @Override public SfxOwnedTask runEntity(Entity entity, Runnable action) {
            AtomicBoolean cancelled = new AtomicBoolean();
            entity.getScheduler().run(plugin, ignored -> { if (!cancelled.get()) action.run(); }, null);
            return track(owner, new FlagTaskHandle(cancelled));
        }
        @Override public SfxOwnedTask runAsync(Runnable action) {
            return task(owner, plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> action.run()));
        }
        @Override public SfxOwnedTask runAsyncRepeating(Duration delay, Duration period, Runnable action) {
            return task(owner, plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, ignored -> action.run(),
                    Math.max(0L, delay.toMillis()), Math.max(1L, period.toMillis()), TimeUnit.MILLISECONDS));
        }
        @Override public <T extends AutoCloseable> T own(T resource) {
            return track(owner, Objects.requireNonNull(resource, "resource"));
        }
    }

    private static final class TaskHandle implements SfxOwnedTask {
        private final ScheduledTask task;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private TaskHandle(ScheduledTask task) { this.task = task; }
        @Override public void cancel() { if (cancelled.compareAndSet(false, true)) task.cancel(); }
        @Override public boolean cancelled() { return cancelled.get(); }
    }

    private static final class FlagTaskHandle implements SfxOwnedTask {
        private final AtomicBoolean cancelled;
        private FlagTaskHandle(AtomicBoolean cancelled) { this.cancelled = cancelled; }
        @Override public void cancel() { cancelled.set(true); }
        @Override public boolean cancelled() { return cancelled.get(); }
    }
}
