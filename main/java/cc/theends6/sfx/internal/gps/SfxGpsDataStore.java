package cc.theends6.sfx.internal.gps;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class SfxGpsDataStore {
    private final JavaPlugin plugin;
    private final SfxGpsDataRepository repository;
    private final File legacyYamlFile;

    private final Map<UUID, Map<String, SfxGpsWaypoint>> waypoints = new ConcurrentHashMap<>();
    private final Map<String, SfxGpsDataSnapshot.GeoResourceRecord> geoResources = new ConcurrentHashMap<>();
    private final Map<String, SfxGpsDataSnapshot.ScannedChunkRecord> scannedChunks = new ConcurrentHashMap<>();
    private final Map<String, SfxGpsDataSnapshot.ElevatorNameRecord> elevatorNames = new ConcurrentHashMap<>();

    private final AtomicLong revision = new AtomicLong();
    private final Map<SfxGpsDataSnapshot.WaypointKey, Long> dirtyWaypoints = new ConcurrentHashMap<>();
    private final Map<SfxGpsDataSnapshot.WaypointKey, Long> deletedWaypoints = new ConcurrentHashMap<>();
    private final Map<String, Long> dirtyGeoResources = new ConcurrentHashMap<>();
    private final Map<String, Long> dirtyScannedChunks = new ConcurrentHashMap<>();
    private final Map<String, Long> dirtyElevatorNames = new ConcurrentHashMap<>();
    private final Map<String, DeletedElevator> deletedElevatorNames = new ConcurrentHashMap<>();

    SfxGpsDataStore(JavaPlugin plugin, SfxGpsDataRepository repository) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.legacyYamlFile = new File(plugin.getDataFolder(), "gps.yml");
    }

    synchronized void load() throws Exception {
        repository.initialize();
        clearMemory();
        SfxGpsDataSnapshot snapshot = repository.loadAll();
        applySnapshot(snapshot, false);
        if (snapshot.isEmpty() && legacyYamlFile.isFile()) {
            int migrated = loadLegacyYaml();
            if (migrated > 0) {
                plugin.getLogger().info("Migrating legacy GPS YAML data to SQLite (" + migrated + " entries). File: " + legacyYamlFile.getName());
                flushAllBlocking();
                File backup = new File(legacyYamlFile.getParentFile(), "gps.yml.migrated." + System.currentTimeMillis() + ".bak");
                if (!legacyYamlFile.renameTo(backup)) {
                    plugin.getLogger().warning("Migrated GPS data to SQLite but failed to rename legacy gps.yml. The file will no longer be written by SFX.");
                } else {
                    plugin.getLogger().info("Legacy GPS YAML migrated and archived as " + backup.getName());
                }
            }
        }
        clearDirtyState();
    }

    synchronized void save() {
        flushAllBlocking();
    }

    synchronized void requestDirtyFlushAsync() {
        persistBatchAsync(snapshotDirty(null, null));
    }

    synchronized void requestChunkFlushAsync(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        persistBatchAsync(snapshotDirty(world.getUID(), new ChunkPos(chunkX, chunkZ)));
    }

    synchronized void flushAllBlocking() {
        DirtyBatch batch = snapshotCurrentAndDirty();
        if (!batch.isEmpty()) {
            try {
                repository.persistChanges(batch.waypointUpserts(), batch.waypointDeletes(), batch.scannedUpserts(), batch.resourceUpserts(), batch.elevatorUpserts(), batch.elevatorDeletes());
                clearPersistedBatch(batch);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to persist SFX GPS data: " + exception.getMessage());
            }
        }
        try {
            repository.awaitPendingWrites();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to drain SFX GPS data writer: " + exception.getMessage());
        }
    }

    synchronized void shutdown() {
        flushAllBlocking();
        repository.close();
        clearMemory();
    }

    synchronized List<SfxGpsWaypoint> waypoints(UUID owner) {
        Map<String, SfxGpsWaypoint> list = waypoints.get(owner);
        return list == null ? List.of() : List.copyOf(list.values());
    }

    synchronized void addWaypoint(SfxGpsWaypoint waypoint) {
        String nameKey = waypointNameKey(waypoint.name());
        SfxGpsDataSnapshot.WaypointKey key = new SfxGpsDataSnapshot.WaypointKey(waypoint.ownerId(), nameKey);
        waypoints.computeIfAbsent(waypoint.ownerId(), ignored -> new LinkedHashMap<>()).put(nameKey, waypoint);
        deletedWaypoints.remove(key);
        markDirty(dirtyWaypoints, key);
    }

    synchronized void removeWaypoint(UUID owner, String name) {
        Map<String, SfxGpsWaypoint> list = waypoints.get(owner);
        if (list == null) {
            return;
        }
        String nameKey = waypointNameKey(name);
        SfxGpsWaypoint removed = list.remove(nameKey);
        if (list.isEmpty()) {
            waypoints.remove(owner);
        }
        if (removed != null) {
            SfxGpsDataSnapshot.WaypointKey key = new SfxGpsDataSnapshot.WaypointKey(owner, nameKey);
            dirtyWaypoints.remove(key);
            markDirty(deletedWaypoints, key);
        }
    }

    synchronized String elevatorName(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        SfxGpsDataSnapshot.ElevatorNameRecord record = elevatorNames.get(elevatorKey(location));
        return record == null ? null : record.name();
    }

    synchronized void setElevatorName(Location location, String name) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        String key = elevatorKey(location);
        SfxGpsDataSnapshot.ElevatorNameRecord previous = elevatorNames.remove(key);
        long now = Instant.now().toEpochMilli();
        if (name == null || name.isBlank()) {
            dirtyElevatorNames.remove(key);
            if (previous != null) {
                deletedElevatorNames.put(key, new DeletedElevator(previous, markRevision()));
            }
            return;
        }
        SfxGpsDataSnapshot.ElevatorNameRecord record = new SfxGpsDataSnapshot.ElevatorNameRecord(
                location.getWorld().getUID(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), name, now);
        elevatorNames.put(key, record);
        deletedElevatorNames.remove(key);
        markDirty(dirtyElevatorNames, key);
    }

    synchronized boolean isScanned(SfxGeoChunkKey key) {
        return scannedChunks.containsKey(key.pathKey());
    }

    synchronized void markScanned(SfxGeoChunkKey key) {
        markScanned(key, null);
    }

    synchronized void markScanned(SfxGeoChunkKey key, Location scanLocation) {
        long now = Instant.now().toEpochMilli();
        scannedChunks.put(key.pathKey(), new SfxGpsDataSnapshot.ScannedChunkRecord(key, now));
        markDirty(dirtyScannedChunks, key.pathKey());
        geoResources.computeIfAbsent(key.pathKey(), ignored -> {
            SfxGpsDataSnapshot.GeoResourceRecord record = new SfxGpsDataSnapshot.GeoResourceRecord(key, generateResources(key, scanLocation), now);
            markDirty(dirtyGeoResources, key.pathKey());
            return record;
        });
    }

    synchronized Map<SfxGeoResourceType, Integer> resources(SfxGeoChunkKey key) {
        return resources(key, null);
    }

    synchronized Map<SfxGeoResourceType, Integer> resources(SfxGeoChunkKey key, Location location) {
        SfxGpsDataSnapshot.GeoResourceRecord record = ensureResourceRecord(key, location);
        EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(record.resources());
        if (normalizeResources(values, location)) {
            record = new SfxGpsDataSnapshot.GeoResourceRecord(key, values, Instant.now().toEpochMilli());
            geoResources.put(key.pathKey(), record);
            markDirty(dirtyGeoResources, key.pathKey());
        }
        return new HashMap<>(record.resources());
    }

    synchronized boolean consume(SfxGeoChunkKey key, SfxGeoResourceType type, int amount) {
        return consume(key, type, amount, null);
    }

    synchronized boolean consume(SfxGeoChunkKey key, SfxGeoResourceType type, int amount, Location location) {
        SfxGpsDataSnapshot.GeoResourceRecord record = ensureResourceRecord(key, location);
        EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(record.resources());
        normalizeResources(values, location);
        int current = values.getOrDefault(type, 0);
        if (current < amount) {
            return false;
        }
        values.put(type, current - amount);
        geoResources.put(key.pathKey(), new SfxGpsDataSnapshot.GeoResourceRecord(key, values, Instant.now().toEpochMilli()));
        markDirty(dirtyGeoResources, key.pathKey());
        return true;
    }

    private SfxGpsDataSnapshot.GeoResourceRecord ensureResourceRecord(SfxGeoChunkKey key, Location location) {
        return geoResources.computeIfAbsent(key.pathKey(), ignored -> {
            SfxGpsDataSnapshot.GeoResourceRecord record = new SfxGpsDataSnapshot.GeoResourceRecord(key, generateResources(key, location), Instant.now().toEpochMilli());
            markDirty(dirtyGeoResources, key.pathKey());
            return record;
        });
    }

    private void persistBatchAsync(DirtyBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        CompletableFuture<Void> future = repository.persistChangesAsync(
                batch.waypointUpserts(), batch.waypointDeletes(), batch.scannedUpserts(), batch.resourceUpserts(), batch.elevatorUpserts(), batch.elevatorDeletes());
        future.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to persist SFX GPS dirty batch: " + throwable.getMessage());
                return;
            }
            synchronized (SfxGpsDataStore.this) {
                clearPersistedBatch(batch);
            }
        });
    }

    private DirtyBatch snapshotDirty(UUID worldId, ChunkPos chunkPos) {
        List<SfxGpsWaypoint> waypointUpserts = new ArrayList<>();
        List<SfxGpsDataSnapshot.WaypointKey> waypointDeletes = new ArrayList<>();
        List<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts = new ArrayList<>();
        List<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts = new ArrayList<>();
        List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts = new ArrayList<>();
        List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes = new ArrayList<>();
        Map<Object, Long> revisions = new HashMap<>();

        if (worldId == null && chunkPos == null) {
            for (Map.Entry<SfxGpsDataSnapshot.WaypointKey, Long> entry : dirtyWaypoints.entrySet()) {
                SfxGpsWaypoint waypoint = waypoint(entry.getKey());
                if (waypoint != null) {
                    waypointUpserts.add(waypoint);
                    revisions.put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<SfxGpsDataSnapshot.WaypointKey, Long> entry : deletedWaypoints.entrySet()) {
                waypointDeletes.add(entry.getKey());
                revisions.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Long> entry : dirtyScannedChunks.entrySet()) {
            SfxGpsDataSnapshot.ScannedChunkRecord record = scannedChunks.get(entry.getKey());
            if (record != null && matchesChunk(record.key(), worldId, chunkPos)) {
                scannedUpserts.add(record);
                revisions.put(new DirtyRef(DirtyKind.SCANNED, entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<String, Long> entry : dirtyGeoResources.entrySet()) {
            SfxGpsDataSnapshot.GeoResourceRecord record = geoResources.get(entry.getKey());
            if (record != null && matchesChunk(record.key(), worldId, chunkPos)) {
                resourceUpserts.add(record);
                revisions.put(new DirtyRef(DirtyKind.RESOURCE, entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<String, Long> entry : dirtyElevatorNames.entrySet()) {
            SfxGpsDataSnapshot.ElevatorNameRecord record = elevatorNames.get(entry.getKey());
            if (record != null && matchesElevatorChunk(record, worldId, chunkPos)) {
                elevatorUpserts.add(record);
                revisions.put(new DirtyRef(DirtyKind.ELEVATOR_UPSERT, entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<String, DeletedElevator> entry : deletedElevatorNames.entrySet()) {
            DeletedElevator deleted = entry.getValue();
            if (deleted != null && matchesElevatorChunk(deleted.record(), worldId, chunkPos)) {
                elevatorDeletes.add(deleted.record());
                revisions.put(new DirtyRef(DirtyKind.ELEVATOR_DELETE, entry.getKey()), deleted.revision());
            }
        }
        return new DirtyBatch(waypointUpserts, waypointDeletes, scannedUpserts, resourceUpserts, elevatorUpserts, elevatorDeletes, revisions);
    }

    private DirtyBatch snapshotCurrentAndDirty() {
        DirtyBatch dirty = snapshotDirty(null, null);
        List<SfxGpsWaypoint> waypointUpserts = new ArrayList<>(dirty.waypointUpserts());
        List<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts = new ArrayList<>(dirty.scannedUpserts());
        List<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts = new ArrayList<>(dirty.resourceUpserts());
        List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts = new ArrayList<>(dirty.elevatorUpserts());
        Map<Object, Long> revisions = new HashMap<>(dirty.revisions());

        for (Map<String, SfxGpsWaypoint> ownerWaypoints : waypoints.values()) {
            for (SfxGpsWaypoint waypoint : ownerWaypoints.values()) {
                SfxGpsDataSnapshot.WaypointKey key = new SfxGpsDataSnapshot.WaypointKey(waypoint.ownerId(), waypointNameKey(waypoint.name()));
                if (!revisions.containsKey(key)) {
                    waypointUpserts.add(waypoint);
                    revisions.put(key, dirtyWaypoints.getOrDefault(key, revision.get()));
                }
            }
        }
        for (Map.Entry<String, SfxGpsDataSnapshot.ScannedChunkRecord> entry : scannedChunks.entrySet()) {
            DirtyRef ref = new DirtyRef(DirtyKind.SCANNED, entry.getKey());
            if (!revisions.containsKey(ref)) {
                scannedUpserts.add(entry.getValue());
                revisions.put(ref, dirtyScannedChunks.getOrDefault(entry.getKey(), revision.get()));
            }
        }
        for (Map.Entry<String, SfxGpsDataSnapshot.GeoResourceRecord> entry : geoResources.entrySet()) {
            DirtyRef ref = new DirtyRef(DirtyKind.RESOURCE, entry.getKey());
            if (!revisions.containsKey(ref)) {
                resourceUpserts.add(entry.getValue());
                revisions.put(ref, dirtyGeoResources.getOrDefault(entry.getKey(), revision.get()));
            }
        }
        for (Map.Entry<String, SfxGpsDataSnapshot.ElevatorNameRecord> entry : elevatorNames.entrySet()) {
            DirtyRef ref = new DirtyRef(DirtyKind.ELEVATOR_UPSERT, entry.getKey());
            if (!revisions.containsKey(ref)) {
                elevatorUpserts.add(entry.getValue());
                revisions.put(ref, dirtyElevatorNames.getOrDefault(entry.getKey(), revision.get()));
            }
        }
        return new DirtyBatch(waypointUpserts, dirty.waypointDeletes(), scannedUpserts, resourceUpserts, elevatorUpserts, dirty.elevatorDeletes(), revisions);
    }

    private void clearPersistedBatch(DirtyBatch batch) {
        for (Map.Entry<Object, Long> entry : batch.revisions().entrySet()) {
            Object key = entry.getKey();
            long batchRevision = entry.getValue();
            if (key instanceof SfxGpsDataSnapshot.WaypointKey waypointKey) {
                removeIfRevisionMatches(dirtyWaypoints, waypointKey, batchRevision);
                removeIfRevisionMatches(deletedWaypoints, waypointKey, batchRevision);
                continue;
            }
            if (key instanceof DirtyRef ref) {
                switch (ref.kind()) {
                    case SCANNED -> removeIfRevisionMatches(dirtyScannedChunks, ref.key(), batchRevision);
                    case RESOURCE -> removeIfRevisionMatches(dirtyGeoResources, ref.key(), batchRevision);
                    case ELEVATOR_UPSERT -> removeIfRevisionMatches(dirtyElevatorNames, ref.key(), batchRevision);
                    case ELEVATOR_DELETE -> {
                        DeletedElevator deleted = deletedElevatorNames.get(ref.key());
                        if (deleted != null && deleted.revision() == batchRevision) {
                            deletedElevatorNames.remove(ref.key());
                        }
                    }
                }
            }
        }
    }

    private <T> void removeIfRevisionMatches(Map<T, Long> map, T key, long batchRevision) {
        map.computeIfPresent(key, (ignored, currentRevision) -> currentRevision == batchRevision ? null : currentRevision);
    }

    private SfxGpsWaypoint waypoint(SfxGpsDataSnapshot.WaypointKey key) {
        Map<String, SfxGpsWaypoint> ownerWaypoints = waypoints.get(key.ownerId());
        return ownerWaypoints == null ? null : ownerWaypoints.get(key.nameKey());
    }

    private boolean matchesChunk(SfxGeoChunkKey key, UUID worldId, ChunkPos chunkPos) {
        if (worldId == null || chunkPos == null) {
            return true;
        }
        return key.worldId().equals(worldId) && key.chunkX() == chunkPos.chunkX() && key.chunkZ() == chunkPos.chunkZ();
    }

    private boolean matchesElevatorChunk(SfxGpsDataSnapshot.ElevatorNameRecord record, UUID worldId, ChunkPos chunkPos) {
        if (worldId == null || chunkPos == null) {
            return true;
        }
        return record.worldId().equals(worldId) && (record.x() >> 4) == chunkPos.chunkX() && (record.z() >> 4) == chunkPos.chunkZ();
    }

    private long markRevision() {
        return revision.incrementAndGet();
    }

    private <T> void markDirty(Map<T, Long> dirtyMap, T key) {
        dirtyMap.put(key, markRevision());
    }

    private String elevatorKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void applySnapshot(SfxGpsDataSnapshot snapshot, boolean markDirty) {
        for (SfxGpsWaypoint waypoint : snapshot.waypoints()) {
            String nameKey = waypointNameKey(waypoint.name());
            waypoints.computeIfAbsent(waypoint.ownerId(), ignored -> new LinkedHashMap<>()).put(nameKey, waypoint);
            if (markDirty) {
                markDirty(dirtyWaypoints, new SfxGpsDataSnapshot.WaypointKey(waypoint.ownerId(), nameKey));
            }
        }
        for (SfxGpsDataSnapshot.ScannedChunkRecord record : snapshot.scannedChunks()) {
            scannedChunks.put(record.key().pathKey(), record);
            if (markDirty) {
                markDirty(dirtyScannedChunks, record.key().pathKey());
            }
        }
        for (SfxGpsDataSnapshot.GeoResourceRecord record : snapshot.geoResources()) {
            geoResources.put(record.key().pathKey(), record);
            if (markDirty) {
                markDirty(dirtyGeoResources, record.key().pathKey());
            }
        }
        for (SfxGpsDataSnapshot.ElevatorNameRecord record : snapshot.elevatorNames()) {
            elevatorNames.put(record.pathKey(), record);
            if (markDirty) {
                markDirty(dirtyElevatorNames, record.pathKey());
            }
        }
    }

    private int loadLegacyYaml() {
        if (!legacyYamlFile.isFile()) {
            return 0;
        }
        List<SfxGpsWaypoint> loadedWaypoints = new ArrayList<>();
        List<SfxGpsDataSnapshot.ScannedChunkRecord> loadedScanned = new ArrayList<>();
        List<SfxGpsDataSnapshot.GeoResourceRecord> loadedResources = new ArrayList<>();
        List<SfxGpsDataSnapshot.ElevatorNameRecord> loadedElevators = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyYamlFile);
        ConfigurationSection waypointRoot = yaml.getConfigurationSection("waypoints");
        if (waypointRoot != null) {
            for (String ownerKey : waypointRoot.getKeys(false)) {
                UUID owner;
                try {
                    owner = UUID.fromString(ownerKey);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                ConfigurationSection ownerSection = waypointRoot.getConfigurationSection(ownerKey);
                if (ownerSection == null) {
                    continue;
                }
                for (String index : ownerSection.getKeys(false)) {
                    ConfigurationSection section = ownerSection.getConfigurationSection(index);
                    if (section == null) {
                        continue;
                    }
                    try {
                        UUID worldId = UUID.fromString(section.getString("worldId", ""));
                        loadedWaypoints.add(new SfxGpsWaypoint(
                                owner,
                                section.getString("name", "Waypoint"),
                                worldId,
                                section.getString("worldName", "world"),
                                section.getDouble("x"),
                                section.getDouble("y"),
                                section.getDouble("z"),
                                (float) section.getDouble("yaw"),
                                (float) section.getDouble("pitch"),
                                section.getLong("createdAt", Instant.now().toEpochMilli())
                        ));
                    } catch (RuntimeException ignored) {
                        
                    }
                }
            }
        }
        ConfigurationSection scanned = yaml.getConfigurationSection("scannedChunks");
        if (scanned != null) {
            for (String key : scanned.getKeys(false)) {
                SfxGeoChunkKey chunkKey = parseLegacyChunkKey(key);
                if (chunkKey != null) {
                    loadedScanned.add(new SfxGpsDataSnapshot.ScannedChunkRecord(chunkKey, scanned.getLong(key)));
                }
            }
        }
        ConfigurationSection resources = yaml.getConfigurationSection("geoResources");
        if (resources != null) {
            for (String key : resources.getKeys(false)) {
                SfxGeoChunkKey chunkKey = parseLegacyChunkKey(key);
                ConfigurationSection section = resources.getConfigurationSection(key);
                if (chunkKey == null || section == null) {
                    continue;
                }
                EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
                for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
                    values.put(type, Math.max(0, section.getInt(type.name().toLowerCase(Locale.ROOT), 0)));
                }
                loadedResources.add(new SfxGpsDataSnapshot.GeoResourceRecord(chunkKey, values, Instant.now().toEpochMilli()));
            }
        }
        ConfigurationSection elevators = yaml.getConfigurationSection("elevators");
        if (elevators != null) {
            for (String key : elevators.getKeys(false)) {
                SfxGpsDataSnapshot.ElevatorNameRecord record = parseLegacyElevatorKey(key, elevators.getString(key, ""));
                if (record != null && !record.name().isBlank()) {
                    loadedElevators.add(record);
                }
            }
        }
        SfxGpsDataSnapshot snapshot = new SfxGpsDataSnapshot(loadedWaypoints, loadedScanned, loadedResources, loadedElevators);
        applySnapshot(snapshot, true);
        return loadedWaypoints.size() + loadedScanned.size() + loadedResources.size() + loadedElevators.size();
    }

    private SfxGeoChunkKey parseLegacyChunkKey(String key) {
        String[] parts = key == null ? new String[0] : key.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            UUID worldId = UUID.fromString(parts[0]);
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);
            World world = Bukkit.getWorld(worldId);
            return new SfxGeoChunkKey(worldId, world == null ? "world" : world.getName(), chunkX, chunkZ);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private SfxGpsDataSnapshot.ElevatorNameRecord parseLegacyElevatorKey(String key, String name) {
        String[] parts = key == null ? new String[0] : key.split(":");
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID worldId = UUID.fromString(parts[0]);
            World world = Bukkit.getWorld(worldId);
            return new SfxGpsDataSnapshot.ElevatorNameRecord(
                    worldId,
                    world == null ? "world" : world.getName(),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    name,
                    Instant.now().toEpochMilli());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void clearMemory() {
        waypoints.clear();
        geoResources.clear();
        scannedChunks.clear();
        elevatorNames.clear();
        clearDirtyState();
    }

    private void clearDirtyState() {
        dirtyWaypoints.clear();
        deletedWaypoints.clear();
        dirtyGeoResources.clear();
        dirtyScannedChunks.clear();
        dirtyElevatorNames.clear();
        deletedElevatorNames.clear();
    }

    private boolean normalizeResources(EnumMap<SfxGeoResourceType, Integer> values, Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return false;
        }
        boolean changed = false;
        World.Environment environment = world.getEnvironment();
        if (environment != World.Environment.NORMAL) {
            changed |= putIfDifferent(values, SfxGeoResourceType.OIL, 0);
            changed |= putIfDifferent(values, SfxGeoResourceType.URANIUM, 0);
        }
        if (environment != World.Environment.NETHER) {
            changed |= putIfDifferent(values, SfxGeoResourceType.NETHER_ICE, 0);
        }
        if (environment != World.Environment.NORMAL && environment != World.Environment.NETHER) {
            changed |= putIfDifferent(values, SfxGeoResourceType.SALT, 0);
        }
        return changed;
    }

    private boolean putIfDifferent(EnumMap<SfxGeoResourceType, Integer> values, SfxGeoResourceType type, int amount) {
        int current = values.getOrDefault(type, 0);
        if (current == amount) {
            return false;
        }
        values.put(type, amount);
        return true;
    }

    private EnumMap<SfxGeoResourceType, Integer> generateResources(SfxGeoChunkKey key) {
        return generateResources(key, null);
    }

    private EnumMap<SfxGeoResourceType, Integer> generateResources(SfxGeoChunkKey key, Location location) {
        EnumMap<SfxGeoResourceType, Integer> values = new EnumMap<>(SfxGeoResourceType.class);
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
                values.put(type, 0);
            }
            return values;
        }
        int sampleY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, location.getBlockY()));
        Biome biome = world.getBlockAt(key.chunkX() << 4, sampleY, key.chunkZ() << 4).getBiome();
        for (SfxGeoResourceType type : SfxGeoResourceType.values()) {
            int base = defaultSupply(type, world.getEnvironment(), biome);
            int amount = base <= 0 ? 0 : base + ThreadLocalRandom.current().nextInt(maxDeviation(type));
            values.put(type, Math.max(0, amount));
        }
        return values;
    }

    private int defaultSupply(SfxGeoResourceType type, World.Environment environment, Biome biome) {
        return switch (type) {
            case OIL -> environment == World.Environment.NORMAL ? oilSupply(biome) : 0;
            case SALT -> {
                if (environment == World.Environment.NORMAL) {
                    yield saltSupply(biome, 6);
                }
                if (environment == World.Environment.NETHER) {
                    yield saltSupply(biome, 8);
                }
                yield 0;
            }
            case URANIUM -> environment == World.Environment.NORMAL ? uraniumSupply(biome) : 0;
            case NETHER_ICE -> environment == World.Environment.NETHER ? netherIceSupply(biome) : 0;
        };
    }

    private int maxDeviation(SfxGeoResourceType type) {
        return switch (type) {
            case OIL -> 8;
            case SALT -> 18;
            case URANIUM -> 2;
            case NETHER_ICE -> 6;
        };
    }

    private int oilSupply(Biome biome) {
        String key = biomeKey(biome);
        return switch (key) {
            case "BEACH", "BEACHES", "STONY_SHORE", "STONE_BEACH", "COLD_BEACH" -> 6;
            case "RIVER" -> 16;
            case "SWAMP", "SWAMPLAND", "MANGROVE_SWAMP" -> 20;
            case "ICE_SPIKES", "FROZEN_OCEAN", "FROZEN_RIVER", "FROZEN_PEAKS", "SNOWY_SLOPES", "ICE_FLATS", "MUTATED_ICE_FLATS" -> 24;
            case "BADLANDS", "WOODED_BADLANDS", "ERODED_BADLANDS", "MESA", "MESA_ROCK", "MESA_CLEAR_ROCK", "MUTATED_MESA", "MUTATED_MESA_ROCK", "MUTATED_MESA_CLEAR_ROCK" -> 40;
            case "DESERT", "DESERT_HILLS", "MUTATED_DESERT" -> 45;
            case "OCEAN", "COLD_OCEAN", "WARM_OCEAN", "LUKEWARM_OCEAN" -> 64;
            case "DEEP_OCEAN", "DEEP_COLD_OCEAN", "DEEP_LUKEWARM_OCEAN", "DEEP_WARM_OCEAN", "DEEP_FROZEN_OCEAN" -> 72;
            case "WINDSWEPT_HILLS", "WINDSWEPT_GRAVELLY_HILLS", "JAGGED_PEAKS", "EXTREME_HILLS", "SMALLER_EXTREME_HILLS", "MUTATED_EXTREME_HILLS", "EXTREME_HILLS_WITH_TREES", "MUTATED_EXTREME_HILLS_WITH_TREES" -> 20;
            case "SNOWY_PLAINS", "SNOWY_TAIGA", "ICE_PLAINS", "TAIGA_COLD", "TAIGA_COLD_HILLS" -> 16;
            case "MUSHROOM_FIELDS", "MUSHROOM_ISLAND", "MUSHROOM_ISLAND_SHORE" -> 20;
            default -> 10;
        };
    }

    private int saltSupply(Biome biome, int fallback) {
        String key = biomeKey(biome);
        return switch (key) {
            case "SWAMP", "SWAMPLAND", "MANGROVE_SWAMP" -> 20;
            case "BEACH", "BEACHES", "COLD_BEACH", "WINDSWEPT_GRAVELLY_HILLS", "STONY_SHORE", "STONE_BEACH", "STONY_PEAKS", "DRIPSTONE_CAVES", "EXTREME_HILLS", "SMALLER_EXTREME_HILLS", "MUTATED_EXTREME_HILLS" -> 40;
            case "OCEAN", "COLD_OCEAN", "WARM_OCEAN", "LUKEWARM_OCEAN", "FROZEN_OCEAN", "DEEP_OCEAN", "DEEP_COLD_OCEAN", "DEEP_LUKEWARM_OCEAN", "DEEP_WARM_OCEAN", "DEEP_FROZEN_OCEAN" -> 60;
            default -> fallback;
        };
    }

    private int uraniumSupply(Biome biome) {
        String key = biomeKey(biome);
        return switch (key) {
            case "DESERT", "DESERT_HILLS", "MUTATED_DESERT", "BEACH", "BEACHES", "STONY_SHORE", "STONE_BEACH", "COLD_BEACH" -> 5;
            case "JAGGED_PEAKS", "STONY_PEAKS", "WINDSWEPT_HILLS", "WINDSWEPT_GRAVELLY_HILLS", "EXTREME_HILLS", "SMALLER_EXTREME_HILLS", "MUTATED_EXTREME_HILLS", "EXTREME_HILLS_WITH_TREES", "MUTATED_EXTREME_HILLS_WITH_TREES" -> 8;
            case "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS", "DRIPSTONE_CAVES", "MESA", "MESA_ROCK", "MESA_CLEAR_ROCK", "MUTATED_MESA", "MUTATED_MESA_ROCK", "MUTATED_MESA_CLEAR_ROCK" -> 12;
            default -> 4;
        };
    }

    private int netherIceSupply(Biome biome) {
        String key = biomeKey(biome);
        return switch (key) {
            case "NETHER_WASTES", "SOUL_SAND_VALLEY", "HELL" -> 32;
            case "CRIMSON_FOREST", "WARPED_FOREST" -> 48;
            case "BASALT_DELTAS" -> 64;
            default -> 32;
        };
    }

    private String biomeKey(Biome biome) {
        return biome == null ? "" : biome.toString().toUpperCase(Locale.ROOT);
    }

    static String waypointNameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static SfxGpsWaypoint waypoint(UUID owner, String name, Location location) {
        return new SfxGpsWaypoint(
                owner,
                name,
                location.getWorld().getUID(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                Instant.now().toEpochMilli()
        );
    }

    private enum DirtyKind {
        SCANNED,
        RESOURCE,
        ELEVATOR_UPSERT,
        ELEVATOR_DELETE
    }

    private record DirtyRef(DirtyKind kind, String key) {
    }

    private record ChunkPos(int chunkX, int chunkZ) {
    }

    private record DeletedElevator(SfxGpsDataSnapshot.ElevatorNameRecord record, long revision) {
    }

    private record DirtyBatch(
            List<SfxGpsWaypoint> waypointUpserts,
            List<SfxGpsDataSnapshot.WaypointKey> waypointDeletes,
            List<SfxGpsDataSnapshot.ScannedChunkRecord> scannedUpserts,
            List<SfxGpsDataSnapshot.GeoResourceRecord> resourceUpserts,
            List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorUpserts,
            List<SfxGpsDataSnapshot.ElevatorNameRecord> elevatorDeletes,
            Map<Object, Long> revisions) {
        private boolean isEmpty() {
            return waypointUpserts.isEmpty()
                    && waypointDeletes.isEmpty()
                    && scannedUpserts.isEmpty()
                    && resourceUpserts.isEmpty()
                    && elevatorUpserts.isEmpty()
                    && elevatorDeletes.isEmpty();
        }
    }
}
