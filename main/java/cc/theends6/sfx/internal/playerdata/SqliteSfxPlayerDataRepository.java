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
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Failed to create player data directory: " + parent.getAbsolutePath());
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing", exception);
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sfx_player_profiles (
                      uuid TEXT PRIMARY KEY,
                      last_name TEXT NOT NULL,
                      guide_layout TEXT,
                      guide_record_history INTEGER NOT NULL DEFAULT 1,
                      guide_close_returns INTEGER NOT NULL DEFAULT 1,
                      guide_fireworks INTEGER NOT NULL DEFAULT 1,
                      guide_unlock_animation INTEGER NOT NULL DEFAULT 1,
                      guide_reopen_last INTEGER NOT NULL DEFAULT 0,
                      guide_last_location TEXT,
                      machine_ui_extended INTEGER NOT NULL DEFAULT 1,
                      machine_completion_sound INTEGER NOT NULL DEFAULT 1,
                      machine_smooth_ui INTEGER NOT NULL DEFAULT 1,
                      radiation_exposure INTEGER NOT NULL DEFAULT 0,
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
            ensureProfileColumn(connection, "guide_layout", "TEXT");
            ensureProfileColumn(connection, "guide_record_history", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "guide_close_returns", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "guide_fireworks", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "guide_unlock_animation", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "guide_reopen_last", "INTEGER NOT NULL DEFAULT 0");
            ensureProfileColumn(connection, "guide_last_location", "TEXT");
            ensureProfileColumn(connection, "machine_ui_extended", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "machine_completion_sound", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "machine_smooth_ui", "INTEGER NOT NULL DEFAULT 1");
            ensureProfileColumn(connection, "radiation_exposure", "INTEGER NOT NULL DEFAULT 0");
        }
    }

    @Override
    public SfxPlayerProfile load(UUID ownerId, String lastKnownName) throws Exception {
        SfxPlayerProfile profile = new SfxPlayerProfile(ownerId, lastKnownName);
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT last_name,
                           guide_layout,
                           guide_record_history,
                           guide_close_returns,
                           guide_fireworks,
                           guide_unlock_animation,
                           guide_reopen_last,
                           guide_last_location,
                           machine_ui_extended,
                           machine_completion_sound,
                           machine_smooth_ui,
                           radiation_exposure
                    FROM sfx_player_profiles
                    WHERE uuid = ?
                    """)) {
                statement.setString(1, ownerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        profile.setLastKnownName(result.getString(1));
                        profile.setGuideLayoutMode(result.getString("guide_layout"));
                        profile.setGuideRecordHistory(result.getInt("guide_record_history") != 0);
                        profile.setGuideCloseReturns(result.getInt("guide_close_returns") != 0);
                        profile.setGuideFireworks(result.getInt("guide_fireworks") != 0);
                        profile.setGuideUnlockAnimation(result.getInt("guide_unlock_animation") != 0);
                        profile.setGuideReopenLastLocation(result.getInt("guide_reopen_last") != 0);
                        profile.setGuideLastLocation(result.getString("guide_last_location"));
                        profile.setMachineUiExtended(result.getInt("machine_ui_extended") != 0);
                        profile.setMachineCompletionSound(result.getInt("machine_completion_sound") != 0);
                        profile.setMachineSmoothUi(result.getInt("machine_smooth_ui") != 0);
                        profile.setRadiationExposure(result.getInt("radiation_exposure"));
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
        if (lastKnownName != null && !lastKnownName.isBlank() && !lastKnownName.equals(profile.lastKnownName())) {
            profile.setLastKnownName(lastKnownName);
        }
        return profile;
    }

    @Override
    public synchronized void save(SfxPlayerProfile profile) throws Exception {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO sfx_player_profiles(
                            uuid,
                            last_name,
                            guide_layout,
                            guide_record_history,
                            guide_close_returns,
                            guide_fireworks,
                            guide_unlock_animation,
                            guide_reopen_last,
                            guide_last_location,
                            machine_ui_extended,
                            machine_completion_sound,
                            machine_smooth_ui,
                            radiation_exposure,
                            updated_at
                        )
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            last_name = excluded.last_name,
                            guide_layout = excluded.guide_layout,
                            guide_record_history = excluded.guide_record_history,
                            guide_close_returns = excluded.guide_close_returns,
                            guide_fireworks = excluded.guide_fireworks,
                            guide_unlock_animation = excluded.guide_unlock_animation,
                            guide_reopen_last = excluded.guide_reopen_last,
                            guide_last_location = excluded.guide_last_location,
                            machine_ui_extended = excluded.machine_ui_extended,
                            machine_completion_sound = excluded.machine_completion_sound,
                            machine_smooth_ui = excluded.machine_smooth_ui,
                            radiation_exposure = excluded.radiation_exposure,
                            updated_at = excluded.updated_at
                        """)) {
                    statement.setString(1, profile.ownerId().toString());
                    statement.setString(2, profile.lastKnownName());
                    statement.setString(3, profile.guideLayoutMode());
                    statement.setInt(4, profile.guideRecordHistory() ? 1 : 0);
                    statement.setInt(5, profile.guideCloseReturns() ? 1 : 0);
                    statement.setInt(6, profile.guideFireworks() ? 1 : 0);
                    statement.setInt(7, profile.guideUnlockAnimation() ? 1 : 0);
                    statement.setInt(8, profile.guideReopenLastLocation() ? 1 : 0);
                    statement.setString(9, profile.guideLastLocation());
                    statement.setInt(10, profile.machineUiExtended() ? 1 : 0);
                    statement.setInt(11, profile.machineCompletionSound() ? 1 : 0);
                    statement.setInt(12, profile.machineSmoothUi() ? 1 : 0);
                    statement.setInt(13, profile.radiationExposure());
                    statement.setLong(14, now);
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
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA synchronous=NORMAL");
        }
        return connection;
    }

    private void ensureProfileColumn(Connection connection, String columnName, String definition) throws SQLException {
        if (hasColumn(connection, "sfx_player_profiles", columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE sfx_player_profiles ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (columnName.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
