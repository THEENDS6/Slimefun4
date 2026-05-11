package cc.theends6.sfx.internal.block;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public final class SqliteSfxBlockDataRepository implements SfxBlockDataRepository {
    private final JavaPlugin plugin;
    private final File databaseFile;
    private final String jdbcUrl;

    public SqliteSfxBlockDataRepository(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
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
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
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
                      updated_at INTEGER NOT NULL
                    )
                    """);
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
        try (Connection connection = openConnection()) {
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
                    SELECT instance_id, type_id, anchor_world, anchor_x, anchor_y, anchor_z, lifecycle_state, version, owner_uuid, state_blob, updated_at
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
                            result.getLong("updated_at")));
                }
            }
        }
        return new SfxBlockDataSnapshot(anchors, instances);
    }

    @Override
    public void upsertAnchor(SfxAnchorRecord anchor) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sfx_block_anchors(world_id, x, y, z, material_key, instance_id, anchor_kind, integrity_state, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(world_id, x, y, z) DO UPDATE SET
                       material_key = excluded.material_key,
                       instance_id = excluded.instance_id,
                       anchor_kind = excluded.anchor_kind,
                       integrity_state = excluded.integrity_state,
                       updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, anchor.key().worldId().toString());
            statement.setInt(2, anchor.key().x());
            statement.setInt(3, anchor.key().y());
            statement.setInt(4, anchor.key().z());
            statement.setString(5, anchor.materialKey());
            statement.setString(6, anchor.instanceId().toString());
            statement.setString(7, anchor.anchorKind().name());
            statement.setString(8, anchor.integrityState().name());
            statement.setLong(9, anchor.updatedAt());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteAnchor(SfxBlockAnchorKey key) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM sfx_block_anchors WHERE world_id = ? AND x = ? AND y = ? AND z = ?
                     """)) {
            statement.setString(1, key.worldId().toString());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            statement.executeUpdate();
        }
    }

    @Override
    public void upsertInstance(SfxBlockInstanceRecord instance) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO sfx_block_instances(instance_id, type_id, anchor_world, anchor_x, anchor_y, anchor_z, lifecycle_state, version, owner_uuid, state_blob, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                       updated_at = excluded.updated_at
                     """)) {
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
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteInstance(UUID instanceId) throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sfx_block_instances WHERE instance_id = ?")) {
            statement.setString(1, instanceId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        plugin.getLogger().fine("Closed SQLite block data repository: " + databaseFile.getAbsolutePath());
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
