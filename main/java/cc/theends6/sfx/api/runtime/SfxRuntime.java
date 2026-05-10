package cc.theends6.sfx.api.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface SfxRuntime {
    JavaPlugin plugin();

    void executeForPlayer(Player player, Runnable task);

    void executeAt(Location location, Runnable task);

    void executeGlobal(Runnable task);

    void executeAsync(Runnable task);

    boolean isOwnedByCurrentRegion(Location location);
}
