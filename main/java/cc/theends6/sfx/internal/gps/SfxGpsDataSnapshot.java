package cc.theends6.sfx.internal.gps;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public record SfxGpsDataSnapshot(
        List<SfxGpsWaypoint> waypoints,
        List<ScannedChunkRecord> scannedChunks,
        List<GeoResourceRecord> geoResources,
        List<ElevatorNameRecord> elevatorNames
) {
    public SfxGpsDataSnapshot {
        waypoints = waypoints == null ? List.of() : List.copyOf(waypoints);
        scannedChunks = scannedChunks == null ? List.of() : List.copyOf(scannedChunks);
        geoResources = geoResources == null ? List.of() : List.copyOf(geoResources);
        elevatorNames = elevatorNames == null ? List.of() : List.copyOf(elevatorNames);
    }

    public boolean isEmpty() {
        return waypoints.isEmpty() && scannedChunks.isEmpty() && geoResources.isEmpty() && elevatorNames.isEmpty();
    }

    public record WaypointKey(UUID ownerId, String nameKey) {
        public WaypointKey {
            java.util.Objects.requireNonNull(ownerId, "ownerId");
            java.util.Objects.requireNonNull(nameKey, "nameKey");
        }
    }

    public record ScannedChunkRecord(SfxGeoChunkKey key, long scannedAt) {
        public ScannedChunkRecord {
            java.util.Objects.requireNonNull(key, "key");
        }
    }

    public record GeoResourceRecord(SfxGeoChunkKey key, EnumMap<SfxGeoResourceType, Integer> resources, long updatedAt) {
        public GeoResourceRecord {
            java.util.Objects.requireNonNull(key, "key");
            resources = resources == null ? new EnumMap<>(SfxGeoResourceType.class) : new EnumMap<>(resources);
        }
    }

    public record ElevatorNameRecord(UUID worldId, String worldName, int x, int y, int z, String name, long updatedAt) {
        public ElevatorNameRecord {
            java.util.Objects.requireNonNull(worldId, "worldId");
            worldName = worldName == null ? "world" : worldName;
            name = name == null ? "" : name;
        }

        public String pathKey() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }
    }
}
