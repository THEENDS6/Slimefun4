package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.internal.persistence.SfxDirtyPersistenceService;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBlockDataService implements SfxDirtyPersistenceService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataRepository repository;
    private final Map<SfxBlockAnchorKey, SfxAnchorRecord> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, SfxBlockInstanceRecord> instances = new ConcurrentHashMap<>();
    private final Set<SfxBlockAnchorKey> dirtyAnchorKeys = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirtyInstanceIds = ConcurrentHashMap.newKeySet();
    private final Set<SfxBlockAnchorKey> pendingAnchorDeletes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SfxBlockAnchorKey> pendingInstanceDeletes = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean shuttingDown;

    public SfxBlockDataService(JavaPlugin plugin, SfxRuntime runtime, SfxBlockDataRepository repository) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.repository = repository;
    }

    public synchronized void initialize() throws Exception {
        repository.initialize();
        SfxBlockDataSnapshot snapshot = repository.loadAll();
        anchors.clear();
        instances.clear();
        dirtyAnchorKeys.clear();
        dirtyInstanceIds.clear();
        pendingAnchorDeletes.clear();
        pendingInstanceDeletes.clear();
        snapshot.anchors().forEach(anchor -> anchors.put(anchor.key(), anchor));
        snapshot.instances().forEach(instance -> instances.put(instance.instanceId(), instance));
        revision.incrementAndGet();
        reconcileLoadedAnchors();
    }

    public Optional<SfxAnchorRecord> findAnchor(Location location) {
        return findAnchorAndValidate(location);
    }

    public Optional<SfxAnchorRecord> findAnchorAndValidate(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.get(key);
        if (anchor == null) {
            return Optional.empty();
        }
        if (anchor.integrityState() != SfxBlockIntegrityState.VALID) {
            if (!isAnchorMaterialValid(location, anchor)) {
                return Optional.empty();
            }
            markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.VALID);
            anchor = anchors.get(key);
            if (anchor == null || anchor.integrityState() != SfxBlockIntegrityState.VALID) {
                return Optional.empty();
            }
        }
        if (!isAnchorMaterialValid(location, anchor)) {
            markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.INVALID);
            return Optional.empty();
        }
        return Optional.of(anchor);
    }

    public Optional<SfxAnchorRecord> findAnchorFast(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        return findAnchorFast(SfxBlockAnchorKey.fromLocation(location));
    }

    public Optional<SfxAnchorRecord> findAnchor(SfxBlockAnchorKey key) {
        return findAnchorFast(key);
    }

    public Optional<SfxAnchorRecord> findAnchorFast(SfxBlockAnchorKey key) {
        if (key == null) {
            return Optional.empty();
        }
        SfxAnchorRecord anchor = anchors.get(key);
        if (anchor == null || anchor.integrityState() != SfxBlockIntegrityState.VALID) {
            return Optional.empty();
        }
        return Optional.of(anchor);
    }

    public long revision() {
        return revision.get();
    }

    public Optional<SfxBlockInstanceRecord> findInstance(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public List<SfxAnchorRecord> anchorsForInstance(UUID instanceId) {
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (SfxAnchorRecord anchor : anchors.values()) {
            if (anchor.instanceId().equals(instanceId) && anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                results.add(anchor);
            }
        }
        return results;
    }

    public List<SfxAnchorRecord> anchors() {
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (SfxAnchorRecord anchor : anchors.values()) {
            if (anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                results.add(anchor);
            }
        }
        return results;
    }

    public UUID registerSingleBlock(String typeId, Location location, Material material, UUID ownerId) {
        return registerSingleBlock(typeId, location, material, ownerId, SfxBlockInstanceRecord.DEFAULT_ENERGY_PRIORITY_DISTANCE);
    }

    public synchronized UUID registerSingleBlock(String typeId, Location location, Material material, UUID ownerId, int energyPriorityDistance) {
        long now = Instant.now().toEpochMilli();
        UUID instanceId = UUID.randomUUID();
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord replaced = anchors.remove(key);
        if (replaced != null) {
            instances.remove(replaced.instanceId());
            dirtyInstanceIds.remove(replaced.instanceId());
            pendingInstanceDeletes.put(replaced.instanceId(), replaced.key());
        }
        SfxBlockInstanceRecord instance = new SfxBlockInstanceRecord(
                instanceId,
                typeId,
                key,
                SfxBlockLifecycleState.IDLE,
                1,
                ownerId,
                new byte[0],
                now,
                energyPriorityDistance);
        SfxAnchorRecord anchor = new SfxAnchorRecord(
                key,
                material.key().toString(),
                instanceId,
                SfxBlockAnchorKind.SINGLE_BLOCK,
                SfxBlockIntegrityState.VALID,
                now);
        pendingInstanceDeletes.remove(instanceId);
        pendingAnchorDeletes.remove(key);
        instances.put(instanceId, instance);
        anchors.put(key, anchor);
        markDirty(anchor, instance);
        revision.incrementAndGet();
        return instanceId;
    }

    public synchronized void updateEnergyPriorityDistance(UUID instanceId, int energyPriorityDistance) {
        SfxBlockInstanceRecord existing = instances.get(instanceId);
        if (existing == null || existing.energyPriorityDistance() == energyPriorityDistance) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withEnergyPriorityDistance(energyPriorityDistance, Instant.now().toEpochMilli());
        instances.put(instanceId, updated);
        markDirty(updated);
    }

    public synchronized void updateInstanceState(UUID instanceId, byte[] stateBlob, SfxBlockLifecycleState lifecycleState) {
        SfxBlockInstanceRecord existing = instances.get(instanceId);
        if (existing == null) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withState(stateBlob, lifecycleState, Instant.now().toEpochMilli());
        instances.put(instanceId, updated);
        markDirty(updated);
    }

    public synchronized void markAnchorIntegrity(Location location, SfxBlockIntegrityState integrityState) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord existing = anchors.get(key);
        if (existing == null) {
            return;
        }
        SfxAnchorRecord updated = updateAnchorIntegrityInMemory(existing, integrityState);
        markDirty(updated);
        revision.incrementAndGet();
    }

    public synchronized void unregisterAt(Location location) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.remove(key);
        if (anchor == null) {
            return;
        }
        UUID instanceId = anchor.instanceId();
        instances.remove(instanceId);
        dirtyAnchorKeys.remove(key);
        dirtyInstanceIds.remove(instanceId);
        pendingAnchorDeletes.add(key);
        pendingInstanceDeletes.put(instanceId, key);
        revision.incrementAndGet();
    }

    @Override
    public void requestDirtyFlushAsync() {
        persistBatchAsync(snapshotAllDirty());
    }

    @Override
    public void requestChunkFlushAsync(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        persistBatchAsync(snapshotDirtyChunk(world.getUID(), chunkX, chunkZ));
    }

    public void flushNow() {
        requestDirtyFlushAsync();
    }

    @Override
    public void flushAllBlocking() {
        PersistenceBatch batch;
        synchronized (this) {
            batch = snapshotAllCurrentAndPending();
        }
        boolean success = true;
        if (!batch.isEmpty()) {
            try {
                repository.persistChanges(batch.anchorUpserts(), batch.instanceUpserts(), batch.anchorDeletes(), batch.instanceDeletes());
            } catch (Exception exception) {
                success = false;
                plugin.getLogger().warning("Failed to persist all SFX block data: " + exception.getMessage());
            }
        }
        try {
            repository.awaitPendingWrites();
        } catch (Exception exception) {
            success = false;
            plugin.getLogger().warning("Failed to drain SFX block data writer: " + exception.getMessage());
        }
        if (success) {
            synchronized (this) {
                dirtyAnchorKeys.clear();
                dirtyInstanceIds.clear();
                pendingAnchorDeletes.clear();
                pendingInstanceDeletes.clear();
            }
        }
    }

    public void flushChunk(World world, int chunkX, int chunkZ) {
        requestChunkFlushAsync(world, chunkX, chunkZ);
    }

    public void reconcileLoadedAnchors() {
        for (World world : Bukkit.getWorlds()) {
            reconcileLoadedWorld(world);
        }
    }

    public void reconcileLoadedWorld(World world) {
        if (world == null) {
            return;
        }
        for (SfxAnchorRecord anchor : anchors.values()) {
            SfxBlockAnchorKey key = anchor.key();
            if (!key.worldId().equals(world.getUID())) {
                continue;
            }
            Location location = new Location(world, key.x(), key.y(), key.z());
            runtime.executeAt(location, () -> reconcileAnchor(world, anchor));
        }
    }

    public void reconcileChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        for (SfxAnchorRecord anchor : anchors.values()) {
            SfxBlockAnchorKey key = anchor.key();
            if (!key.worldId().equals(world.getUID()) || (key.x() >> 4) != chunkX || (key.z() >> 4) != chunkZ) {
                continue;
            }
            Location location = new Location(world, key.x(), key.y(), key.z());
            runtime.executeAt(location, () -> reconcileAnchor(world, anchor));
        }
    }

    @Override
    public synchronized void shutdown() {
        shuttingDown = true;
        flushAllBlocking();
        repository.close();
        anchors.clear();
        instances.clear();
        dirtyAnchorKeys.clear();
        dirtyInstanceIds.clear();
        pendingAnchorDeletes.clear();
        pendingInstanceDeletes.clear();
    }

    public int anchorCount() {
        return anchors.size();
    }

    public int instanceCount() {
        return instances.size();
    }

    private void reconcileAnchor(World world, SfxAnchorRecord anchor) {
        SfxBlockAnchorKey key = anchor.key();
        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        if (anchor.integrityState() != SfxBlockIntegrityState.VALID) {
            if (isAnchorMaterialValid(block.getLocation(), anchor)) {
                markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.VALID);
            }
            return;
        }
        if (!isAnchorMaterialValid(block.getLocation(), anchor)) {
            markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.INVALID);
        }
    }

    private boolean isAnchorMaterialValid(Location location, SfxAnchorRecord anchor) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        Material actual = location.getBlock().getType();
        if (actual.isAir()) {
            return false;
        }
        if (actual.key().toString().equals(anchor.materialKey())) {
            return true;
        }
        SfxBlockInstanceRecord instance = instances.get(anchor.instanceId());
        if (instance != null && "sf:gps_teleporter_pylon".equals(instance.typeId())) {
            return actual == Material.CYAN_STAINED_GLASS
                    || actual == Material.PURPLE_STAINED_GLASS
                    || actual == Material.RED_STAINED_GLASS;
        }
        if (instance != null && isDynamicDecorationMaterialValid(instance.typeId(), actual)) {
            return true;
        }
        return false;
    }

    private boolean isDynamicDecorationMaterialValid(String typeId, Material actual) {
        if (typeId == null || actual == null || actual.isAir()) {
            return false;
        }
        String id = typeId.toLowerCase(java.util.Locale.ROOT);
        String material = actual.name();
        if (id.startsWith("sf:rainbow_glass_pane")) {
            return material.endsWith("_STAINED_GLASS_PANE");
        }
        if (id.startsWith("sf:rainbow_glass")) {
            return material.endsWith("_STAINED_GLASS") && !material.endsWith("_PANE");
        }
        if (id.startsWith("sf:rainbow_wool")) {
            return material.endsWith("_WOOL");
        }
        if (id.startsWith("sf:rainbow_clay")) {
            return material.endsWith("_TERRACOTTA") && !material.endsWith("_GLAZED_TERRACOTTA");
        }
        if (id.startsWith("sf:rainbow_concrete")) {
            return material.endsWith("_CONCRETE");
        }
        if (id.startsWith("sf:rainbow_glazed_terracotta")) {
            return material.endsWith("_GLAZED_TERRACOTTA");
        }
        return false;
    }

    private synchronized void markAnchorIntegritySync(SfxBlockAnchorKey key, SfxAnchorRecord existing, SfxBlockIntegrityState integrityState) {
        if (existing.integrityState() == integrityState) {
            return;
        }
        SfxAnchorRecord updated = updateAnchorIntegrityInMemory(existing, integrityState);
        markDirty(updated);
        revision.incrementAndGet();
        if (integrityState == SfxBlockIntegrityState.INVALID) {
            plugin.getLogger().warning("Marked invalid SFX block anchor at " + key + " because the world block no longer matches " + existing.materialKey());
        }
    }

    private SfxAnchorRecord updateAnchorIntegrityInMemory(SfxAnchorRecord existing, SfxBlockIntegrityState integrityState) {
        SfxAnchorRecord updated = existing.withIntegrity(integrityState, Instant.now().toEpochMilli());
        anchors.put(existing.key(), updated);
        return updated;
    }

    private void markDirty(SfxAnchorRecord anchor, SfxBlockInstanceRecord instance) {
        markDirty(anchor);
        markDirty(instance);
    }

    private void markDirty(SfxAnchorRecord anchor) {
        if (anchor != null) {
            dirtyAnchorKeys.add(anchor.key());
        }
    }

    private void markDirty(SfxBlockInstanceRecord instance) {
        if (instance != null) {
            dirtyInstanceIds.add(instance.instanceId());
        }
    }

    private synchronized PersistenceBatch snapshotAllDirty() {
        return snapshotDirty(null, 0, 0);
    }

    private synchronized PersistenceBatch snapshotDirtyChunk(UUID worldId, int chunkX, int chunkZ) {
        return snapshotDirty(new ChunkSelector(worldId, chunkX, chunkZ), chunkX, chunkZ);
    }

    private PersistenceBatch snapshotDirty(ChunkSelector selector, int chunkX, int chunkZ) {
        Set<SfxBlockAnchorKey> dirtyAnchors = new HashSet<>();
        Set<UUID> dirtyInstances = new HashSet<>();
        Set<SfxBlockAnchorKey> anchorDeletes = new HashSet<>();
        Set<UUID> instanceDeletes = new HashSet<>();
        Map<UUID, SfxBlockInstanceRecord> instanceUpserts = new HashMap<>();
        Map<SfxBlockAnchorKey, SfxAnchorRecord> anchorUpserts = new HashMap<>();

        for (SfxBlockAnchorKey key : new HashSet<>(dirtyAnchorKeys)) {
            if (!matches(selector, key)) {
                continue;
            }
            SfxAnchorRecord anchor = anchors.get(key);
            if (anchor == null) {
                continue;
            }
            dirtyAnchors.add(key);
            anchorUpserts.put(key, anchor);
            SfxBlockInstanceRecord instance = instances.get(anchor.instanceId());
            if (instance != null) {
                instanceUpserts.put(instance.instanceId(), instance);
            }
        }

        for (UUID instanceId : new HashSet<>(dirtyInstanceIds)) {
            SfxBlockInstanceRecord instance = instances.get(instanceId);
            if (instance == null || !matches(selector, instance.anchorKey())) {
                continue;
            }
            dirtyInstances.add(instanceId);
            instanceUpserts.put(instanceId, instance);
        }

        for (SfxBlockAnchorKey key : new HashSet<>(pendingAnchorDeletes)) {
            if (matches(selector, key)) {
                anchorDeletes.add(key);
            }
        }

        for (Map.Entry<UUID, SfxBlockAnchorKey> entry : new HashSet<>(pendingInstanceDeletes.entrySet())) {
            if (matches(selector, entry.getValue())) {
                instanceDeletes.add(entry.getKey());
            }
        }

        dirtyAnchorKeys.removeAll(dirtyAnchors);
        dirtyInstanceIds.removeAll(dirtyInstances);
        pendingAnchorDeletes.removeAll(anchorDeletes);
        Map<UUID, SfxBlockAnchorKey> instanceDeleteAnchorKeys = new HashMap<>();
        for (UUID instanceId : instanceDeletes) {
            SfxBlockAnchorKey deleteKey = pendingInstanceDeletes.remove(instanceId);
            if (deleteKey != null) {
                instanceDeleteAnchorKeys.put(instanceId, deleteKey);
            }
        }

        return new PersistenceBatch(
                new ArrayList<>(anchorUpserts.values()),
                new ArrayList<>(instanceUpserts.values()),
                new ArrayList<>(anchorDeletes),
                new ArrayList<>(instanceDeletes),
                dirtyAnchors,
                dirtyInstances,
                anchorDeletes,
                instanceDeletes,
                instanceDeleteAnchorKeys);
    }

    private PersistenceBatch snapshotAllCurrentAndPending() {
        Set<SfxBlockAnchorKey> allAnchorKeys = new HashSet<>(dirtyAnchorKeys);
        allAnchorKeys.addAll(anchors.keySet());
        Set<UUID> allInstanceIds = new HashSet<>(dirtyInstanceIds);
        allInstanceIds.addAll(instances.keySet());
        Map<SfxBlockAnchorKey, SfxAnchorRecord> anchorUpserts = new HashMap<>();
        Map<UUID, SfxBlockInstanceRecord> instanceUpserts = new HashMap<>();
        for (SfxBlockAnchorKey key : allAnchorKeys) {
            SfxAnchorRecord anchor = anchors.get(key);
            if (anchor != null) {
                anchorUpserts.put(key, anchor);
            }
        }
        for (UUID instanceId : allInstanceIds) {
            SfxBlockInstanceRecord instance = instances.get(instanceId);
            if (instance != null) {
                instanceUpserts.put(instanceId, instance);
            }
        }
        Set<SfxBlockAnchorKey> anchorDeletes = new HashSet<>(pendingAnchorDeletes);
        Set<UUID> instanceDeletes = new HashSet<>(pendingInstanceDeletes.keySet());
        Map<UUID, SfxBlockAnchorKey> instanceDeleteAnchorKeys = new HashMap<>(pendingInstanceDeletes);
        return new PersistenceBatch(
                new ArrayList<>(anchorUpserts.values()),
                new ArrayList<>(instanceUpserts.values()),
                new ArrayList<>(anchorDeletes),
                new ArrayList<>(instanceDeletes),
                new HashSet<>(dirtyAnchorKeys),
                new HashSet<>(dirtyInstanceIds),
                anchorDeletes,
                instanceDeletes,
                instanceDeleteAnchorKeys);
    }

    private boolean matches(ChunkSelector selector, SfxBlockAnchorKey key) {
        return selector == null || selector.matches(key);
    }

    private void persistBatchAsync(PersistenceBatch batch) {
        if (batch.isEmpty()) {
            return;
        }
        CompletableFuture<Void> future = repository.persistChangesAsync(batch.anchorUpserts(), batch.instanceUpserts(), batch.anchorDeletes(), batch.instanceDeletes());
        future.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                return;
            }
            plugin.getLogger().warning("Failed to persist SFX block data batch: " + throwable.getMessage());
            restoreDirtyBatch(batch);
        });
    }

    private synchronized void restoreDirtyBatch(PersistenceBatch batch) {
        dirtyAnchorKeys.addAll(batch.dirtyAnchorKeys());
        dirtyInstanceIds.addAll(batch.dirtyInstanceIds());
        pendingAnchorDeletes.addAll(batch.anchorDeleteKeys());
        for (UUID instanceId : batch.instanceDeleteIds()) {
            SfxBlockAnchorKey key = batch.instanceDeleteAnchorKeys().get(instanceId);
            if (key != null) {
                pendingInstanceDeletes.put(instanceId, key);
            }
        }
    }

    private record ChunkSelector(UUID worldId, int chunkX, int chunkZ) {
        boolean matches(SfxBlockAnchorKey key) {
            return key.worldId().equals(worldId) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ;
        }
    }

    private record PersistenceBatch(
            List<SfxAnchorRecord> anchorUpserts,
            List<SfxBlockInstanceRecord> instanceUpserts,
            List<SfxBlockAnchorKey> anchorDeletes,
            List<UUID> instanceDeletes,
            Set<SfxBlockAnchorKey> dirtyAnchorKeys,
            Set<UUID> dirtyInstanceIds,
            Set<SfxBlockAnchorKey> anchorDeleteKeys,
            Set<UUID> instanceDeleteIds,
            Map<UUID, SfxBlockAnchorKey> instanceDeleteAnchorKeys) {
        boolean isEmpty() {
            return anchorUpserts.isEmpty() && instanceUpserts.isEmpty() && anchorDeletes.isEmpty() && instanceDeletes.isEmpty();
        }
    }
}
