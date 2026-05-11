package cc.theends6.sfx.internal.playerdata;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public final class SqliteSfxPlayerDataRepository implements SfxPlayerDataRepository {
    private final JavaPlugin plugin;
    private final File databaseFile;
    private final String jdbcUrl;

    public SqliteSfxPlayerDataRepository(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    @Override
    public void initialize() throws SQLException {
        databaseFile.getParentFile().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing", exception);
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_player_profiles (
                      uuid TEXT PRIMARY KEY,
                      last_name TEXT NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_player_researches (
                      owner_uuid TEXT NOT NULL,
                      research_id TEXT NOT NULL,
                      unlocked_at INTEGER NOT NULL,
                      PRIMARY KEY (owner_uuid, research_id),
                      FOREIGN KEY (owner_uuid) REFERENCES sfx_player_profiles(uuid) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_player_backpacks (
                      owner_uuid TEXT NOT NULL,
                      backpack_id INTEGER NOT NULL,
                      size INTEGER NOT NULL,
                      contents BLOB NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY (owner_uuid, backpack_id),
                      FOREIGN KEY (owner_uuid) REFERENCES sfx_player_profiles(uuid) ON DELETE CASCADE
                    )
                    """);
        }
    }

    @Override
    public SfxPlayerProfile load(UUID ownerId, String lastKnownName) throws Exception {
        SfxPlayerProfile profile = new SfxPlayerProfile(ownerId, lastKnownName);
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT last_name FROM sfx_player_profiles WHERE uuid = ?")) {
                statement.setString(1, ownerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        profile.setLastKnownName(result.getString(1));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT research_id FROM sfx_player_researches WHERE owner_uuid = ?")) {
                statement.setString(1, ownerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        profile.unlock(result.getString(1));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT backpack_id, size, contents, updated_at FROM sfx_player_backpacks WHERE owner_uuid = ? ORDER BY backpack_id")) {
                statement.setString(1, ownerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        int id = result.getInt("backpack_id");
                        int size = result.getInt("size");
                        byte[] contentsBlob = result.getBytes("contents");
                        long updatedAt = result.getLong("updated_at");
                        profile.putBackpack(new SfxBackpackRecord(id, size, SfxItemStackCodec.decode(contentsBlob), updatedAt));
                    }
                }
            }
        }
        profile.markSaved();
        profile.setLastKnownName(lastKnownName);
        profile.markSaved();
        return profile;
    }

    @Override
    public void save(SfxPlayerProfile profile) throws Exception {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO sfx_player_profiles(uuid, last_name, updated_at)
                        VALUES(?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET last_name = excluded.last_name, updated_at = excluded.updated_at
                        """)) {
                    statement.setString(1, profile.ownerId().toString());
                    statement.setString(2, profile.lastKnownName());
                    statement.setLong(3, now);
                    statement.executeUpdate();
                }

                try (PreparedStatement deleteResearches = connection.prepareStatement("DELETE FROM sfx_player_researches WHERE owner_uuid = ?")) {
                    deleteResearches.setString(1, profile.ownerId().toString());
                    deleteResearches.executeUpdate();
                }
                try (PreparedStatement insertResearch = connection.prepareStatement(
                        "INSERT INTO sfx_player_researches(owner_uuid, research_id, unlocked_at) VALUES(?, ?, ?)")) {
                    for (String researchId : profile.unlockedResearchesCopy()) {
                        insertResearch.setString(1, profile.ownerId().toString());
                        insertResearch.setString(2, researchId);
                        insertResearch.setLong(3, now);
                        insertResearch.addBatch();
                    }
                    insertResearch.executeBatch();
                }

                try (PreparedStatement deleteBackpacks = connection.prepareStatement("DELETE FROM sfx_player_backpacks WHERE owner_uuid = ?")) {
                    deleteBackpacks.setString(1, profile.ownerId().toString());
                    deleteBackpacks.executeUpdate();
                }
                try (PreparedStatement insertBackpack = connection.prepareStatement(
                        "INSERT INTO sfx_player_backpacks(owner_uuid, backpack_id, size, contents, updated_at) VALUES(?, ?, ?, ?, ?)")) {
                    for (SfxBackpackRecord backpack : profile.backpacksCopy().values()) {
                        insertBackpack.setString(1, profile.ownerId().toString());
                        insertBackpack.setInt(2, backpack.id());
                        insertBackpack.setInt(3, backpack.size());
                        insertBackpack.setBytes(4, SfxItemStackCodec.encode(backpack.contentsCopy()));
                        insertBackpack.setLong(5, backpack.updatedAt() > 0L ? backpack.updatedAt() : now);
                        insertBackpack.addBatch();
                    }
                    insertBackpack.executeBatch();
                }

                connection.commit();
                profile.markSaved();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        plugin.getLogger().fine("Closed SQLite player data repository: " + databaseFile.getAbsolutePath());
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
