package cc.theends6.sfx.internal.topology;

import java.util.UUID;
import org.bukkit.Location;

public record SfxNodeKey(UUID worldId, int x, int y, int z) {
    public static SfxNodeKey from(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location/world required");
        }
        return new SfxNodeKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public SfxNodeKey offset(int dx, int dy, int dz) {
        return new SfxNodeKey(worldId, x + dx, y + dy, z + dz);
    }
}
