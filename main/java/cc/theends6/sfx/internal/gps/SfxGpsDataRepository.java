package cc.theends6.sfx.internal.gps;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface SfxGpsDataRepository {
    void initialize() throws Exception;

    SfxGpsDataSnapshot loadAll() throws Exception;

    CompletableFuture<Void> persistChangesAsync(
            Collection<SfxGpsWaypoint> waypointUpserts,
            Collection<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            Collection<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            Collection<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes);

    void persistChanges(
            Collection<SfxGpsWaypoint> waypointUpserts,
            Collection<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            Collection<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            Collection<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes) throws Exception;

    void awaitPendingWrites() throws Exception;

    void close();
}
