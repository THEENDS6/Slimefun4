package cc.theends6.sfx.api.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.function.Supplier;

public interface SfxRuntime {
    JavaPlugin plugin();

    void executeForPlayer(Player player, Runnable task);

    void executeForPlayerLater(Player player, long delayTicks, Runnable task);

    void executeAt(Location location, Runnable task);

    void executeAtLater(Location location, long delayTicks, Runnable task);

    void executeGlobal(Runnable task);

    void executeGlobalLater(long delayTicks, Runnable task);

    void executeAsync(Runnable task);

    boolean isOwnedByCurrentRegion(Location location);

    <T> T supplyAt(Location location, Supplier<T> supplier);
}
