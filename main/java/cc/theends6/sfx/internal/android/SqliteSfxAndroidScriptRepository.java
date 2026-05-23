package cc.theends6.sfx.internal.android;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public final class SqliteSfxAndroidScriptRepository implements AutoCloseable {
    private final JavaPlugin plugin;
    private final File databaseFile;

    public SqliteSfxAndroidScriptRepository(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
    }

    public void initialize() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Failed to create Android script database directory: " + parent);
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS sfx_android_scripts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "android_type TEXT NOT NULL,"
                    + "author_uuid TEXT NOT NULL,"
                    + "author_name TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "code TEXT NOT NULL,"
                    + "visibility TEXT NOT NULL DEFAULT 'PUBLIC',"
                    + "downloads INTEGER NOT NULL DEFAULT 0,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "deleted_at INTEGER"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS sfx_android_script_votes ("
                    + "script_id INTEGER NOT NULL,"
                    + "voter_uuid TEXT NOT NULL,"
                    + "vote INTEGER NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "PRIMARY KEY (script_id, voter_uuid)"
                    + ")");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sfx_android_scripts_type_visibility ON sfx_android_scripts(android_type, visibility, deleted_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sfx_android_scripts_author ON sfx_android_scripts(author_uuid, deleted_at)");
        }
    }

    public synchronized long upload(SfxAndroidType type, UUID authorId, String authorName, String name, List<SfxAndroidInstruction> body, SfxAndroidScriptVisibility visibility) throws SQLException {
        long now = Instant.now().toEpochMilli();
        String code = SfxAndroidScriptCodec.toReadableScript(body);
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sfx_android_scripts(android_type, author_uuid, author_name, name, code, visibility, downloads, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, type.key());
            statement.setString(2, authorId.toString());
            statement.setString(3, authorName == null || authorName.isBlank() ? authorId.toString() : authorName);
            statement.setString(4, name == null || name.isBlank() ? "Android Script" : name.trim());
            statement.setString(5, code);
            statement.setString(6, visibility.name());
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    public synchronized List<SfxAndroidScriptRecord> listVisible(SfxAndroidType type, UUID viewerId, int offset, int limit) throws SQLException {
        List<SfxAndroidScriptRecord> result = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT s.*,"
                        + " COALESCE(SUM(CASE WHEN v.vote > 0 THEN 1 ELSE 0 END), 0) AS positive_votes,"
                        + " COALESCE(SUM(CASE WHEN v.vote < 0 THEN 1 ELSE 0 END), 0) AS negative_votes"
                        + " FROM sfx_android_scripts s"
                        + " LEFT JOIN sfx_android_script_votes v ON v.script_id = s.id"
                        + " WHERE s.deleted_at IS NULL AND s.android_type = ?"
                        + " AND (s.visibility = 'PUBLIC' OR s.author_uuid = ?)"
                        + " GROUP BY s.id"
                        + " ORDER BY s.downloads DESC, positive_votes DESC, s.updated_at DESC"
                        + " LIMIT ? OFFSET ?")) {
            statement.setString(1, type.key());
            statement.setString(2, viewerId.toString());
            statement.setInt(3, Math.max(1, Math.min(45, limit)));
            statement.setInt(4, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readRecord(rs, type));
                }
            }
        }
        return result;
    }

    public synchronized SfxAndroidScriptRecord find(long id) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT s.*,"
                        + " COALESCE(SUM(CASE WHEN v.vote > 0 THEN 1 ELSE 0 END), 0) AS positive_votes,"
                        + " COALESCE(SUM(CASE WHEN v.vote < 0 THEN 1 ELSE 0 END), 0) AS negative_votes"
                        + " FROM sfx_android_scripts s"
                        + " LEFT JOIN sfx_android_script_votes v ON v.script_id = s.id"
                        + " WHERE s.deleted_at IS NULL AND s.id = ?"
                        + " GROUP BY s.id")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readRecord(rs, null);
            }
        }
    }

    public synchronized void incrementDownloads(long id) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE sfx_android_scripts SET downloads = downloads + 1, updated_at = ? WHERE id = ? AND deleted_at IS NULL")) {
            statement.setLong(1, Instant.now().toEpochMilli());
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    public synchronized boolean softDelete(long id, UUID actorId, boolean force) throws SQLException {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                force
                        ? "UPDATE sfx_android_scripts SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL"
                        : "UPDATE sfx_android_scripts SET deleted_at = ?, updated_at = ? WHERE id = ? AND author_uuid = ? AND deleted_at IS NULL")) {
            long now = Instant.now().toEpochMilli();
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setLong(3, id);
            if (!force) {
                statement.setString(4, actorId.toString());
            }
            return statement.executeUpdate() > 0;
        }
    }

    public synchronized void vote(long scriptId, UUID voterId, int vote) throws SQLException {
        if (vote != -1 && vote != 1) {
            throw new IllegalArgumentException("Vote must be -1 or 1");
        }
        long now = Instant.now().toEpochMilli();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sfx_android_script_votes(script_id, voter_uuid, vote, created_at, updated_at) VALUES (?, ?, ?, ?, ?)"
                        + " ON CONFLICT(script_id, voter_uuid) DO UPDATE SET vote = excluded.vote, updated_at = excluded.updated_at")) {
            statement.setLong(1, scriptId);
            statement.setString(2, voterId.toString());
            statement.setInt(3, vote);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    private SfxAndroidScriptRecord readRecord(ResultSet rs, SfxAndroidType knownType) throws SQLException {
        SfxAndroidType type = knownType;
        if (type == null) {
            String rawType = rs.getString("android_type");
            for (SfxAndroidType candidate : SfxAndroidType.values()) {
                if (candidate.key().equals(rawType)) {
                    type = candidate;
                    break;
                }
            }
        }
        if (type == null) {
            type = SfxAndroidType.NORMAL;
        }
        List<SfxAndroidInstruction> body;
        try {
            body = SfxAndroidScriptCodec.parseReadableScript(type, rs.getString("code"));
        } catch (RuntimeException exception) {
            body = List.of(SfxAndroidInstruction.WAIT);
        }
        return new SfxAndroidScriptRecord(
                rs.getLong("id"),
                type,
                UUID.fromString(rs.getString("author_uuid")),
                rs.getString("author_name"),
                rs.getString("name"),
                body,
                SfxAndroidScriptVisibility.parse(rs.getString("visibility")),
                rs.getInt("downloads"),
                rs.getInt("positive_votes"),
                rs.getInt("negative_votes"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
        return connection;
    }

    @Override
    public void close() {
        // This repository opens short-lived connections. Nothing to close here.
    }
}
