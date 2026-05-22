package cc.theends6.sfx.internal.gps;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.java.JavaPlugin;

public final class SqliteSfxGpsDataRepository implements SfxGpsDataRepository {
    private final JavaPlugin plugin;
    private final File databaseFile;
    private final String jdbcUrl;
    private final ExecutorService writer;
    private final Object writeLock = new Object();
    private volatile CompletableFuture<Void> writeTail = CompletableFuture.completedFuture(null);
    private Connection writerConnection;
    private volatile boolean closed;

    public SqliteSfxGpsDataRepository(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "SFX-GPSData-SQLite-Writer-" + index.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void initialize() throws Exception {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create GPS data directory: " + parent.getAbsolutePath());
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing", exception);
        }
        try (Connection connection = openTransientConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_gps_waypoints (
                      owner_uuid TEXT NOT NULL,
                      name_key TEXT NOT NULL,
                      name TEXT NOT NULL,
                      world_id TEXT NOT NULL,
                      world_name TEXT NOT NULL,
                      x REAL NOT NULL,
                      y REAL NOT NULL,
                      z REAL NOT NULL,
                      yaw REAL NOT NULL,
                      pitch REAL NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY (owner_uuid, name_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_gps_scanned_chunks (
                      world_id TEXT NOT NULL,
                      world_name TEXT NOT NULL,
                      chunk_x INTEGER NOT NULL,
                      chunk_z INTEGER NOT NULL,
                      scanned_at INTEGER NOT NULL,
                      PRIMARY KEY (world_id, chunk_x, chunk_z)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_gps_geo_resources (
                      world_id TEXT NOT NULL,
                      world_name TEXT NOT NULL,
                      chunk_x INTEGER NOT NULL,
                      chunk_z INTEGER NOT NULL,
                      resource_type TEXT NOT NULL,
                      amount INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY (world_id, chunk_x, chunk_z, resource_type)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_gps_elevator_names (
                      world_id TEXT NOT NULL,
                      world_name TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      name TEXT NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY (world_id, x, y, z)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sfx_gps_waypoints_owner ON sfx_gps_waypoints(owner_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sfx_gps_geo_chunk ON sfx_gps_geo_resources(world_id, chunk_x, chunk_z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sfx_gps_elevators_world_chunk ON sfx_gps_elevator_names(world_id, x, z)");
        }
    }

    @Override
    public SfxGpsDataSnapshot loadAll() throws Exception {
        List<SfxGpsWaypoint> waypoints = new ArrayList<>();
        List<SfxGpsDataSnapshot.ScannedChunkRecord> scanned = new ArrayList<>();
        List<SfxGpsDataSnapshot.GeoResourceRecord> resources = new ArrayList<>();
        List<SfxGpsDataSnapshot.ElevatorNameRecord> elevators = new ArrayList<>();
        try (Connection connection = openTransientConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT owner_uuid, name, world_id, world_name, x, y, z, yaw, pitch, created_at
                    FROM sfx_gps_waypoints
                    ORDER BY owner_uuid, created_at, name
                    """); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    waypoints.add(new SfxGpsWaypoint(
                            UUID.fromString(result.getString("owner_uuid")),
                            result.getString("name"),
                            UUID.fromString(result.getString("world_id")),
                            result.getString("world_name"),
                            result.getDouble("x"),
                            result.getDouble("y"),
                            result.getDouble("z"),
                            (float) result.getDouble("yaw"),
                            (float) result.getDouble("pitch"),
                            result.getLong("created_at")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT world_id, world_name, chunk_x, chunk_z, scanned_at
                    FROM sfx_gps_scanned_chunks
                    """); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    scanned.add(new SfxGpsDataSnapshot.ScannedChunkRecord(
                            new SfxGeoChunkKey(UUID.fromString(result.getString("world_id")), result.getString("world_name"), result.getInt("chunk_x"), result.getInt("chunk_z")),
                            result.getLong("scanned_at")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT world_id, world_name, chunk_x, chunk_z, resource_type, amount, updated_at
                    FROM sfx_gps_geo_resources
                    ORDER BY world_id, chunk_x, chunk_z
                    """); ResultSet result = statement.executeQuery()) {
                String currentKey = null;
                SfxGeoChunkKey currentChunk = null;
                long updatedAt = 0L;
                EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
                while (result.next()) {
                    SfxGeoChunkKey chunk = new SfxGeoChunkKey(UUID.fromString(result.getString("world_id")), result.getString("world_name"), result.getInt("chunk_x"), result.getInt("chunk_z"));
                    String pathKey = chunk.pathKey();
                    if (currentKey != null && !currentKey.equals(pathKey)) {
                        resources.add(new SfxGpsDataSnapshot.GeoResourceRecord(currentChunk, values, updatedAt));
                        values = new EnumMap<>(SfxGeoResourceType.class);
                    }
                    currentKey = pathKey;
                    currentChunk = chunk;
                    updatedAt = Math.max(updatedAt, result.getLong("updated_at"));
                    try {
                        values.put(SfxGeoResourceType.valueOf(result.getString("resource_type")), Math.max(0, result.getInt("amount")));
                    } catch (IllegalArgumentException ignored) {
                        
                    }
                }
                if (currentKey != null) {
                    resources.add(new SfxGpsDataSnapshot.GeoResourceRecord(currentChunk, values, updatedAt));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT world_id, world_name, x, y, z, name, updated_at
                    FROM sfx_gps_elevator_names
                    """); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    elevators.add(new SfxGpsDataSnapshot.ElevatorNameRecord(
                            UUID.fromString(result.getString("world_id")),
                            result.getString("world_name"),
                            result.getInt("x"),
                            result.getInt("y"),
                            result.getInt("z"),
                            result.getString("name"),
                            result.getLong("updated_at")));
                }
            }
        }
        return new SfxGpsDataSnapshot(waypoints, scanned, resources, elevators);
    }

    @Override
    public CompletableFuture<Void> persistChangesAsync(
            Collection<SfxGpsWaypoint> waypointUpserts,
            Collection<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            Collection<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            Collection<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes) {
        if (isEmpty(waypointUpserts) && isEmpty(waypointDeletes) && isEmpty(scannedUpserts) && isEmpty(resourceUpserts) && isEmpty(elevatorUpserts) && isEmpty(elevatorDeletes)) {
            return CompletableFuture.completedFuture(null);
        }
        if (closed) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("SFX GPS data repository is already closed"));
            return failed;
        }
        WriteBatch batch = new WriteBatch(
                waypointUpserts == null ? List.of() : List.copyOf(waypointUpserts),
                waypointDeletes == null ? List.of() : List.copyOf(waypointDeletes),
                scannedUpserts == null ? List.of() : List.copyOf(scannedUpserts),
                resourceUpserts == null ? List.of() : List.copyOf(resourceUpserts),
                elevatorUpserts == null ? List.of() : List.copyOf(elevatorUpserts),
                elevatorDeletes == null ? List.of() : List.copyOf(elevatorDeletes));
        CompletableFuture<Void> next;
        synchronized (writeLock) {
            next = writeTail.handle((ignored, previousError) -> null).thenRunAsync(() -> {
                try {
                    persistChangesDirect(batch);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }, writer);
            writeTail = next;
        }
        return next;
    }

    @Override
    public void persistChanges(
            Collection<SfxGpsWaypoint> waypointUpserts,
            Collection<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            Collection<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            Collection<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes) throws Exception {
        persistChangesAsync(waypointUpserts, waypointDeletes, scannedUpserts, resourceUpserts, elevatorUpserts, elevatorDeletes).get();
    }

    @Override
    public void awaitPendingWrites() throws Exception {
        writeTail.get();
    }

    @Override
    public void close() {
        closed = true;
        try {
            awaitPendingWrites();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed while waiting for SFX GPS data writer: " + exception.getMessage());
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
        closeWriterConnection();
        plugin.getLogger().fine("Closed SQLite GPS data repository: " + databaseFile.getAbsolutePath());
    }

    private void persistChangesDirect(WriteBatch batch) throws Exception {
        Connection connection = writerConnection();
        synchronized (writeLock) {
            connection.setAutoCommit(false);
            try {
                deleteWaypoints(connection, batch.waypointDeletes());
                upsertWaypoints(connection, batch.waypointUpserts());
                upsertScanned(connection, batch.scannedUpserts());
                upsertResources(connection, batch.resourceUpserts());
                deleteElevators(connection, batch.elevatorDeletes());
                upsertElevators(connection, batch.elevatorUpserts());
                connection.commit();
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void upsertWaypoints(Connection connection, Collection<SfxGpsWaypoint> waypoints) throws SQLException {
        if (isEmpty(waypoints)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sfx_gps_waypoints(owner_uuid, name_key, name, world_id, world_name, x, y, z, yaw, pitch, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner_uuid, name_key) DO UPDATE SET
                  name = excluded.name,
                  world_id = excluded.world_id,
                  world_name = excluded.world_name,
                  x = excluded.x,
                  y = excluded.y,
                  z = excluded.z,
                  yaw = excluded.yaw,
                  pitch = excluded.pitch,
                  created_at = excluded.created_at,
                  updated_at = excluded.updated_at
                """)) {
            long now = System.currentTimeMillis();
            for (SfxGpsWaypoint waypoint : waypoints) {
                statement.setString(1, waypoint.ownerId().toString());
                statement.setString(2, SfxGpsDataStore.waypointNameKey(waypoint.name()));
                statement.setString(3, waypoint.name());
                statement.setString(4, waypoint.worldId().toString());
                statement.setString(5, waypoint.worldName());
                statement.setDouble(6, waypoint.x());
                statement.setDouble(7, waypoint.y());
                statement.setDouble(8, waypoint.z());
                statement.setFloat(9, waypoint.yaw());
                statement.setFloat(10, waypoint.pitch());
                statement.setLong(11, waypoint.createdAt());
                statement.setLong(12, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteWaypoints(Connection connection, Collection<SfxGpsDataSnapshot.WaypointKey> deletes) throws SQLException {
        if (isEmpty(deletes)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sfx_gps_waypoints WHERE owner_uuid = ? AND name_key = ?")) {
            for (SfxGpsDataSnapshot.WaypointKey key : deletes) {
                statement.setString(1, key.ownerId().toString());
                statement.setString(2, key.nameKey());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertScanned(Connection connection, Collection<SfxGpsDataSnapshot.ScannedChunkRecord> scanned) throws SQLException {
        if (isEmpty(scanned)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sfx_gps_scanned_chunks(world_id, world_name, chunk_x, chunk_z, scanned_at)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(world_id, chunk_x, chunk_z) DO UPDATE SET
                  world_name = excluded.world_name,
                  scanned_at = excluded.scanned_at
                """)) {
            for (SfxGpsDataSnapshot.ScannedChunkRecord record : scanned) {
                SfxGeoChunkKey key = record.key();
                statement.setString(1, key.worldId().toString());
                statement.setString(2, key.worldName());
                statement.setInt(3, key.chunkX());
                statement.setInt(4, key.chunkZ());
                statement.setLong(5, record.scannedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertResources(Connection connection, Collection<SfxGpsDataSnapshot.GeoResourceRecord> resources) throws SQLException {
        if (isEmpty(resources)) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM sfx_gps_geo_resources WHERE world_id = ? AND chunk_x = ? AND chunk_z = ?");
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO sfx_gps_geo_resources(world_id, world_name, chunk_x, chunk_z, resource_type, amount, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?)
                     """)) {
            for (SfxGpsDataSnapshot.GeoResourceRecord record : resources) {
                SfxGeoChunkKey key = record.key();
                delete.setString(1, key.worldId().toString());
                delete.setInt(2, key.chunkX());
                delete.setInt(3, key.chunkZ());
                delete.addBatch();
                for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
                    insert.setString(1, key.worldId().toString());
                    insert.setString(2, key.worldName());
                    insert.setInt(3, key.chunkX());
                    insert.setInt(4, key.chunkZ());
                    insert.setString(5, type.name());
                    insert.setInt(6, Math.max(0, record.resources().getOrDefault(type, 0)));
                    insert.setLong(7, record.updatedAt());
                    insert.addBatch();
                }
            }
            delete.executeBatch();
            insert.executeBatch();
        }
    }

    private void upsertElevators(Connection connection, Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevators) throws SQLException {
        if (isEmpty(elevators)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sfx_gps_elevator_names(world_id, world_name, x, y, z, name, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(world_id, x, y, z) DO UPDATE SET
                  world_name = excluded.world_name,
                  name = excluded.name,
                  updated_at = excluded.updated_at
                """)) {
            for (SfxGpsDataSnapshot.ElevatorNameRecord record : elevators) {
                statement.setString(1, record.worldId().toString());
                statement.setString(2, record.worldName());
                statement.setInt(3, record.x());
                statement.setInt(4, record.y());
                statement.setInt(5, record.z());
                statement.setString(6, record.name());
                statement.setLong(7, record.updatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteElevators(Connection connection, Collection<SfxGpsDataSnapshot.ElevatorNameRecord> elevators) throws SQLException {
        if (isEmpty(elevators)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM sfx_gps_elevator_names WHERE world_id = ? AND x = ? AND y = ? AND z = ?")) {
            for (SfxGpsDataSnapshot.ElevatorNameRecord record : elevators) {
                statement.setString(1, record.worldId().toString());
                statement.setInt(2, record.x());
                statement.setInt(3, record.y());
                statement.setInt(4, record.z());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Connection writerConnection() throws SQLException {
        if (writerConnection == null || writerConnection.isClosed()) {
            writerConnection = openTransientConnection();
        }
        return writerConnection;
    }

    private Connection openTransientConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
        return connection;
    }

    private void closeWriterConnection() {
        try {
            if (writerConnection != null) {
                writerConnection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().fine("Failed to close SFX GPS data SQLite writer connection: " + exception.getMessage());
        } finally {
            writerConnection = null;
        }
    }

    private boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    private record WriteBatch(
            List<SfxGpsWaypoint> waypointUpserts,
            List<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            List<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            List<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes) {
    }
}
