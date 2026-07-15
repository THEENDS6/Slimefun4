package cc.theends6.sfx.internal.virtualcontainer;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

public record SfxVirtualContainerKey(UUID worldId, int x1, int y1, int z1, int x2, int y2, int z2) {
    public SfxVirtualContainerKey {
        Objects.requireNonNull(worldId, "worldId");
        int ax = Math.min(x1, x2);
        int ay = Math.min(y1, y2);
        int az = Math.min(z1, z2);
        int bx = Math.max(x1, x2);
        int by = Math.max(y1, y2);
        int bz = Math.max(z1, z2);
        x1 = ax;
        y1 = ay;
        z1 = az;
        x2 = bx;
        y2 = by;
        z2 = bz;
    }

    public static SfxVirtualContainerKey single(SfxBlockAnchorKey key) {
        return new SfxVirtualContainerKey(key.worldId(), key.x(), key.y(), key.z(), key.x(), key.y(), key.z());
    }

    public static SfxVirtualContainerKey single(Location location) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        return single(key);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public boolean isSingle() {
        return x1 == x2 && y1 == y2 && z1 == z2;
    }

    @Override
    public String toString() {
        if (isSingle()) {
            return worldId + ":" + x1 + ":" + y1 + ":" + z1;
        }
        return worldId + ":" + x1 + ":" + y1 + ":" + z1 + "~" + x2 + ":" + y2 + ":" + z2;
    }
}
