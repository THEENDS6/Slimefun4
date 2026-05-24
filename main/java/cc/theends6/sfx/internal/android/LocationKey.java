package cc.theends6.sfx.internal.android;

import java.util.UUID;
import org.bukkit.Location;

record LocationKey(UUID worldId, int x, int y, int z) {
    static LocationKey of(Location location) {
        return new LocationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
