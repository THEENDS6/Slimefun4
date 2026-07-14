package cc.theends6.sfx.internal.runtime;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.ServerTickManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperSfxRuntime implements SfxRuntime {
    private static final long CROSS_REGION_WAIT_TIMEOUT_MILLIS = 1000L;
    private volatile long lastCrossRegionWarningMillis;

    private final JavaPlugin plugin;

    public PaperSfxRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public void executeForPlayer(Player player, Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void executeForPlayerLater(Player player, long delayTicks, Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1L, delayTicks));
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    @Override
    public void executeAtLater(Location location, long delayTicks, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), Math.max(1L, delayTicks));
    }

    @Override
    public void executeGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    @Override
    public void executeGlobalLater(long delayTicks, Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), Math.max(1L, delayTicks));
    }

    @Override
    public void executeAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public boolean isGameTickFrozen() {
        try {
            ServerTickManager tickManager = plugin.getServer().getServerTickManager();
            return tickManager != null
                    && tickManager.isFrozen()
                    && !tickManager.isStepping()
                    && !tickManager.isSprinting()
                    && tickManager.getFrozenTicksToRun() <= 0;
        } catch (LinkageError | UnsupportedOperationException exception) {
            return false;
        }
    }

    @Override
    public <T> T supplyAt(Location location, Supplier<T> supplier) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(supplier, "supplier");
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("Plugin is disabled");
        }
        if (isOwnedByCurrentRegion(location)) {
            return supplier.get();
        }
        logCrossRegionBlockingWarning(location);
        try {
            return supplyAtAsync(location, supplier).get(CROSS_REGION_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for region task at " + location, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Region task failed at " + location, cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for region task at " + location + " after " + CROSS_REGION_WAIT_TIMEOUT_MILLIS + "ms", e);
        }
    }

    @Override
    public <T> CompletableFuture<T> supplyAtAsync(Location location, Supplier<T> supplier) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(supplier, "supplier");
        if (!plugin.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Plugin is disabled"));
        }
        if (isOwnedByCurrentRegion(location)) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable throwable) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void logCrossRegionBlockingWarning(Location location) {
        long now = System.currentTimeMillis();
        long previous = lastCrossRegionWarningMillis;
        if (now - previous < 30_000L) {
            return;
        }
        lastCrossRegionWarningMillis = now;
        plugin.getLogger().warning("Blocking cross-region supplyAt call at " + location + ". This path should be migrated to supplyAtAsync on Folia.");
    }

    @Override
    public boolean isOwnedByCurrentRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return plugin.getServer().isOwnedByCurrentRegion(location);
    }
}
