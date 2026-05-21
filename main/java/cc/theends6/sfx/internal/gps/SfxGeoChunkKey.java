package cc.theends6.sfx.internal.gps;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;

public record SfxGeoChunkKey(UUID worldId, String worldName, int chunkX, int chunkZ) {
    public SfxGeoChunkKey {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
    }

    public static SfxGeoChunkKey from(Location location) {
        return from(location.getChunk());
    }

    public static SfxGeoChunkKey from(Chunk chunk) {
        return new SfxGeoChunkKey(chunk.getWorld().getUID(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String pathKey() {
        return worldId + ":" + chunkX + ":" + chunkZ;
    }
}
