package cc.theends6.sfx.internal.runtime;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperSfxRuntime implements SfxRuntime {
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
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    @Override
    public void executeForPlayerLater(Player player, long delayTicks, Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1L, delayTicks));
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(task, "task");
        plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    @Override
    public void executeAtLater(Location location, long delayTicks, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location.world");
        Objects.requireNonNull(task, "task");
        plugin.getServer().getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), Math.max(1L, delayTicks));
    }

    @Override
    public void executeGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    @Override
    public void executeGlobalLater(long delayTicks, Runnable task) {
        Objects.requireNonNull(task, "task");
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), Math.max(1L, delayTicks));
    }

    @Override
    public void executeAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public boolean isOwnedByCurrentRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return plugin.getServer().isOwnedByCurrentRegion(location);
    }
}
