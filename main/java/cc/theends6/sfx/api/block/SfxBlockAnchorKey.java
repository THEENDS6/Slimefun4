package cc.theends6.sfx.api.block;

import java.util.UUID;
import org.bukkit.Location;

public record SfxBlockAnchorKey(UUID worldId, int x, int y, int z) {
    public static SfxBlockAnchorKey fromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location/world is required");
        }
        return new SfxBlockAnchorKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
