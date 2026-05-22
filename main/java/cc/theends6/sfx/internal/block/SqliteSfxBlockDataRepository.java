package cc.theends6.sfx.internal.block;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.java.JavaPlugin;

public final class SqliteSfxBlockDataRepository implements SfxBlockDataRepository {
    private final JavaPlugin plugin;
    private final File databaseFile;
    private final String jdbcUrl;
    private final ExecutorService writer;
    private final Object writeLock = new Object();
    private volatile CompletableFuture<Void> writeTail = CompletableFuture.completedFuture(null);
    private Connection writerConnection;
    private PreparedStatement upsertAnchorStatement;
    private PreparedStatement deleteAnchorStatement;
    private PreparedStatement upsertInstanceStatement;
    private PreparedStatement deleteInstanceStatement;
    private volatile boolean closed;

    public SqliteSfxBlockDataRepository(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        this.writer = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "SFX-BlockData-SQLite-Writer-" + index.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void initialize() throws Exception {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create block data directory: " + parent.getAbsolutePath());
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing", exception);
        }
        try (Connection connection = openTransientConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_block_instances (
                      instance_id TEXT PRIMARY KEY,
                      type_id TEXT NOT NULL,
                      anchor_world TEXT NOT NULL,
                      anchor_x INTEGER NOT NULL,
                      anchor_y INTEGER NOT NULL,
                      anchor_z INTEGER NOT NULL,
                      lifecycle_state TEXT NOT NULL,
                      version INTEGER NOT NULL,
                      owner_uuid TEXT,
                      state_blob BLOB NOT NULL,
                      updated_at INTEGER NOT NULL,
                      energy_priority_distance INTEGER NOT NULL DEFAULT 2147483647
                    )
                    """);
            ensureColumn(connection, "sfx_block_instances", "energy_priority_distance", "INTEGER NOT NULL DEFAULT 2147483647");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_block_anchors (
                      world_id TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      material_key TEXT NOT NULL,
                      instance_id TEXT NOT NULL,
                      anchor_kind TEXT NOT NULL,
                      integrity_state TEXT NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY (world_id, x, y, z),
                      FOREIGN KEY (instance_id) REFERENCES sfx_block_instances(instance_id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    @Override
    public SfxBlockDataSnapshot loadAll() throws Exception {
        List<SfxAnchorRecord> anchors = new ArrayList<>();
        List<SfxBlockInstanceRecord> instances = new ArrayList<>();
        try (Connection connection = openTransientConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT world_id, x, y, z, material_key, instance_id, anchor_kind, integrity_state, updated_at
                    FROM sfx_block_anchors
                    ORDER BY world_id, y, x, z
                    """);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    anchors.add(new SfxAnchorRecord(
                            new SfxBlockAnchorKey(
                                    UUID.fromString(result.getString("world_id")),
                                    result.getInt("x"),
                                    result.getInt("y"),
                                    result.getInt("z")),
                            result.getString("material_key"),
                            UUID.fromString(result.getString("instance_id")),
                            SfxBlockAnchorKind.valueOf(result.getString("anchor_kind")),
                            SfxBlockIntegrityState.valueOf(result.getString("integrity_state")),
                            result.getLong("updated_at")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT instance_id, type_id, anchor_world, anchor_x, anchor_y, anchor_z, lifecycle_state, version, owner_uuid, state_blob, updated_at, energy_priority_distance
                    FROM sfx_block_instances
                    ORDER BY updated_at DESC
                    """);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String owner = result.getString("owner_uuid");
                    instances.add(new SfxBlockInstanceRecord(
                            UUID.fromString(result.getString("instance_id")),
                            result.getString("type_id"),
                            new SfxBlockAnchorKey(
                                    UUID.fromString(result.getString("anchor_world")),
                                    result.getInt("anchor_x"),
                                    result.getInt("anchor_y"),
                                    result.getInt("anchor_z")),
                            SfxBlockLifecycleState.valueOf(result.getString("lifecycle_state")),
                            result.getInt("version"),
                            owner == null ? null : UUID.fromString(owner),
                            result.getBytes("state_blob"),
                            result.getLong("updated_at"),
                            result.getInt("energy_priority_distance")));
                }
            }
        }
        return new SfxBlockDataSnapshot(anchors, instances);
    }

    @Override
    public CompletableFuture<Void> persistChangesAsync(
            Collection<SfxAnchorRecord> anchors,
            Collection<SfxBlockInstanceRecord> instances,
            Collection<SfxBlockAnchorKey> anchorDeletes,
            Collection<UUID> instanceDeletes) {
        if (isEmpty(anchors) && isEmpty(instances) && isEmpty(anchorDeletes) && isEmpty(instanceDeletes)) {
            return CompletableFuture.completedFuture(null);
        }
        if (closed) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("SFX block data repository is already closed"));
            return failed;
        }
        WriteBatch batch = new WriteBatch(
                anchors == null ? List.of() : List.copyOf(anchors),
                instances == null ? List.of() : List.copyOf(instances),
                anchorDeletes == null ? List.of() : List.copyOf(anchorDeletes),
                instanceDeletes == null ? List.of() : List.copyOf(instanceDeletes));
        CompletableFuture<Void> next;
        synchronized (writeLock) {
            next = writeTail.handle((ignored, previousError) -> null).thenRunAsync(() -> {
                try {
                    persistChangesDirect(batch.anchors(), batch.instances(), batch.anchorDeletes(), batch.instanceDeletes());
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
            Collection<SfxAnchorRecord> anchors,
            Collection<SfxBlockInstanceRecord> instances,
            Collection<SfxBlockAnchorKey> anchorDeletes,
            Collection<UUID> instanceDeletes) throws Exception {
        if (isEmpty(anchors) && isEmpty(instances) && isEmpty(anchorDeletes) && isEmpty(instanceDeletes)) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("SFX block data repository is already closed");
        }
        persistChangesAsync(anchors, instances, anchorDeletes, instanceDeletes).get();
    }

    @Override
    public void upsertAnchor(SfxAnchorRecord anchor) throws Exception {
        persistChanges(List.of(anchor), List.of(), List.of(), List.of());
    }

    @Override
    public void deleteAnchor(SfxBlockAnchorKey key) throws Exception {
        persistChanges(List.of(), List.of(), List.of(key), List.of());
    }

    @Override
    public void upsertInstance(SfxBlockInstanceRecord instance) throws Exception {
        persistChanges(List.of(), List.of(instance), List.of(), List.of());
    }

    @Override
    public void deleteInstance(UUID instanceId) throws Exception {
        persistChanges(List.of(), List.of(), List.of(), List.of(instanceId));
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
            plugin.getLogger().warning("Failed while waiting for SFX block data writer: " + exception.getMessage());
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
        plugin.getLogger().fine("Closed SQLite block data repository: " + databaseFile.getAbsolutePath());
    }

    private void persistChangesDirect(
            Collection<SfxAnchorRecord> anchors,
            Collection<SfxBlockInstanceRecord> instances,
            Collection<SfxBlockAnchorKey> anchorDeletes,
            Collection<UUID> instanceDeletes) throws Exception {
        if (isEmpty(anchors) && isEmpty(instances) && isEmpty(anchorDeletes) && isEmpty(instanceDeletes)) {
            return;
        }
        Connection connection = writerConnection();
        synchronized (writeLock) {
            connection.setAutoCommit(false);
            try {
                batchUpsertInstances(connection, instances);
                batchUpsertAnchors(connection, anchors);
                batchDeleteAnchors(connection, anchorDeletes);
                batchDeleteInstances(connection, instanceDeletes);
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

    private void batchUpsertAnchors(Connection connection, Collection<SfxAnchorRecord> anchors) throws SQLException {
        if (isEmpty(anchors)) {
            return;
        }
        PreparedStatement statement = upsertAnchorStatement(connection);
        statement.clearBatch();
        for (SfxAnchorRecord anchor : anchors) {
                statement.setString(1, anchor.key().worldId().toString());
                statement.setInt(2, anchor.key().x());
                statement.setInt(3, anchor.key().y());
                statement.setInt(4, anchor.key().z());
                statement.setString(5, anchor.materialKey());
                statement.setString(6, anchor.instanceId().toString());
                statement.setString(7, anchor.anchorKind().name());
                statement.setString(8, anchor.integrityState().name());
                statement.setLong(9, anchor.updatedAt());
                statement.addBatch();
        }
        statement.executeBatch();
    }

    private void batchDeleteAnchors(Connection connection, Collection<SfxBlockAnchorKey> anchorDeletes) throws SQLException {
        if (isEmpty(anchorDeletes)) {
            return;
        }
        PreparedStatement statement = deleteAnchorStatement(connection);
        statement.clearBatch();
        for (SfxBlockAnchorKey key : anchorDeletes) {
                statement.setString(1, key.worldId().toString());
                statement.setInt(2, key.x());
                statement.setInt(3, key.y());
                statement.setInt(4, key.z());
                statement.addBatch();
        }
        statement.executeBatch();
    }

    private void batchUpsertInstances(Connection connection, Collection<SfxBlockInstanceRecord> instances) throws SQLException {
        if (isEmpty(instances)) {
            return;
        }
        PreparedStatement statement = upsertInstanceStatement(connection);
        statement.clearBatch();
        for (SfxBlockInstanceRecord instance : instances) {
                statement.setString(1, instance.instanceId().toString());
                statement.setString(2, instance.typeId());
                statement.setString(3, instance.anchorKey().worldId().toString());
                statement.setInt(4, instance.anchorKey().x());
                statement.setInt(5, instance.anchorKey().y());
                statement.setInt(6, instance.anchorKey().z());
                statement.setString(7, instance.lifecycleState().name());
                statement.setInt(8, instance.version());
                if (instance.ownerId() == null) {
                    statement.setString(9, null);
                } else {
                    statement.setString(9, instance.ownerId().toString());
                }
                statement.setBytes(10, instance.stateBlob());
                statement.setLong(11, instance.updatedAt());
                statement.setInt(12, instance.energyPriorityDistance());
                statement.addBatch();
        }
        statement.executeBatch();
    }

    private void batchDeleteInstances(Connection connection, Collection<UUID> instanceDeletes) throws SQLException {
        if (isEmpty(instanceDeletes)) {
            return;
        }
        PreparedStatement statement = deleteInstanceStatement(connection);
        statement.clearBatch();
        for (UUID instanceId : instanceDeletes) {
                statement.setString(1, instanceId.toString());
                statement.addBatch();
        }
        statement.executeBatch();
    }

    private PreparedStatement upsertAnchorStatement(Connection connection) throws SQLException {
        if (upsertAnchorStatement == null || upsertAnchorStatement.isClosed()) {
            upsertAnchorStatement = connection.prepareStatement("""
                    INSERT INTO sfx_block_anchors(world_id, x, y, z, material_key, instance_id, anchor_kind, integrity_state, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(world_id, x, y, z) DO UPDATE SET
                      material_key = excluded.material_key,
                      instance_id = excluded.instance_id,
                      anchor_kind = excluded.anchor_kind,
                      integrity_state = excluded.integrity_state,
                      updated_at = excluded.updated_at
                    """);
        }
        return upsertAnchorStatement;
    }

    private PreparedStatement deleteAnchorStatement(Connection connection) throws SQLException {
        if (deleteAnchorStatement == null || deleteAnchorStatement.isClosed()) {
            deleteAnchorStatement = connection.prepareStatement("DELETE FROM sfx_block_anchors WHERE world_id = ? AND x = ? AND y = ? AND z = ?");
        }
        return deleteAnchorStatement;
    }

    private PreparedStatement upsertInstanceStatement(Connection connection) throws SQLException {
        if (upsertInstanceStatement == null || upsertInstanceStatement.isClosed()) {
            upsertInstanceStatement = connection.prepareStatement("""
                    INSERT INTO sfx_block_instances(instance_id, type_id, anchor_world, anchor_x, anchor_y, anchor_z, lifecycle_state, version, owner_uuid, state_blob, updated_at, energy_priority_distance)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(instance_id) DO UPDATE SET
                      type_id = excluded.type_id,
                      anchor_world = excluded.anchor_world,
                      anchor_x = excluded.anchor_x,
                      anchor_y = excluded.anchor_y,
                      anchor_z = excluded.anchor_z,
                      lifecycle_state = excluded.lifecycle_state,
                      version = excluded.version,
                      owner_uuid = excluded.owner_uuid,
                      state_blob = excluded.state_blob,
                      updated_at = excluded.updated_at,
                      energy_priority_distance = excluded.energy_priority_distance
                    """);
        }
        return upsertInstanceStatement;
    }

    private PreparedStatement deleteInstanceStatement(Connection connection) throws SQLException {
        if (deleteInstanceStatement == null || deleteInstanceStatement.isClosed()) {
            deleteInstanceStatement = connection.prepareStatement("DELETE FROM sfx_block_instances WHERE instance_id = ?");
        }
        return deleteInstanceStatement;
    }

    private void ensureColumn(Connection connection, String table, String column, String declaration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + declaration);
        }
    }

    private Connection writerConnection() throws SQLException {
        if (writerConnection == null || writerConnection.isClosed()) {
            writerConnection = openTransientConnection();
        }
        return writerConnection;
    }

    private void closeWriterConnection() {
        closeStatement(upsertAnchorStatement);
        closeStatement(deleteAnchorStatement);
        closeStatement(upsertInstanceStatement);
        closeStatement(deleteInstanceStatement);
        upsertAnchorStatement = null;
        deleteAnchorStatement = null;
        upsertInstanceStatement = null;
        deleteInstanceStatement = null;
        try {
            if (writerConnection != null) {
                writerConnection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().fine("Failed to close SFX block data SQLite writer connection: " + exception.getMessage());
        } finally {
            writerConnection = null;
        }
    }

    private void closeStatement(PreparedStatement statement) {
        if (statement == null) {
            return;
        }
        try {
            statement.close();
        } catch (SQLException exception) {
            plugin.getLogger().fine("Failed to close SFX block data prepared statement: " + exception.getMessage());
        }
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

    private boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    private record WriteBatch(
            List<SfxAnchorRecord> anchors,
            List<SfxBlockInstanceRecord> instances,
            List<SfxBlockAnchorKey> anchorDeletes,
            List<UUID> instanceDeletes) {
    }
}
