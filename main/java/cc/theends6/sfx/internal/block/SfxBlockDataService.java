package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

import cc.theends6.sfx.internal.persistence.SfxDirtyPersistenceService;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBlockDataService implements SfxDirtyPersistenceService {
    public interface AnchorChangeListener {
        void onAnchorAdded(SfxAnchorRecord anchor);

        void onAnchorUpdated(SfxAnchorRecord anchor);

        void onAnchorRemoved(SfxBlockAnchorKey key);
    }

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataRepository repository;
    private final Map<SfxBlockAnchorKey, SfxAnchorRecord> anchors = new ConcurrentHashMap<>();
    
    private final Map<UUID, SfxBlockInstanceRecord> instances = new ConcurrentHashMap<>();
    
    private final Map<UUID, SfxBlockInstanceRecord> instanceHeaders = new ConcurrentHashMap<>();
    private final Map<ChunkIndexKey, Set<SfxBlockAnchorKey>> anchorsByChunk = new ConcurrentHashMap<>();
    private final Set<ChunkIndexKey> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<UUID>> instancesByType = new ConcurrentHashMap<>();
    private final Map<ChunkIndexKey, DirtyChunkState> dirtyByChunk = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkIndexKey> dirtyInstanceChunks = new ConcurrentHashMap<>();
    private final List<AnchorChangeListener> anchorChangeListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> materialVariants = new ConcurrentHashMap<>();
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
        SfxBlockDataSnapshot snapshot = repository.loadIndex();
        anchors.clear();
        instances.clear();
        instanceHeaders.clear();
        anchorsByChunk.clear();
        loadedChunks.clear();
        instancesByType.clear();
        dirtyByChunk.clear();
        dirtyInstanceChunks.clear();
        dirtyAnchorKeys.clear();
        dirtyInstanceIds.clear();
        pendingAnchorDeletes.clear();
        pendingInstanceDeletes.clear();
        snapshot.anchors().forEach(anchor -> {
            anchors.put(anchor.key(), anchor);
            indexAnchor(anchor.key());
        });
        snapshot.instances().forEach(instance -> {
            instanceHeaders.put(instance.instanceId(), instance);
            indexInstanceType(instance);
        });
        revision.incrementAndGet();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                attachChunk(chunk);
            }
        }
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

    public void addAnchorChangeListener(AnchorChangeListener listener) {
        if (listener != null) {
            anchorChangeListeners.add(listener);
        }
    }

    public void removeAnchorChangeListener(AnchorChangeListener listener) {
        if (listener != null) {
            anchorChangeListeners.remove(listener);
        }
    }

    private void notifyAnchorAdded(SfxAnchorRecord anchor) {
        for (AnchorChangeListener listener : anchorChangeListeners) {
            listener.onAnchorAdded(anchor);
        }
    }

    private void notifyAnchorUpdated(SfxAnchorRecord anchor) {
        for (AnchorChangeListener listener : anchorChangeListeners) {
            listener.onAnchorUpdated(anchor);
        }
    }

    private void notifyAnchorRemoved(SfxBlockAnchorKey key) {
        for (AnchorChangeListener listener : anchorChangeListeners) {
            listener.onAnchorRemoved(key);
        }
    }

    public Optional<SfxBlockInstanceRecord> findInstance(UUID instanceId) {
        return Optional.ofNullable(instanceId == null ? null : instances.containsKey(instanceId)
                ? instances.get(instanceId) : instanceHeaders.get(instanceId));
    }

    
    public synchronized Optional<SfxBlockInstanceRecord> materializeInstance(UUID instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        SfxBlockInstanceRecord header = instanceHeaders.get(instanceId);
        SfxBlockInstanceRecord current = instances.get(instanceId);
        if (current != null && (dirtyInstanceIds.contains(instanceId)
                || hasLoadedAnchor(instanceId)
                || current.hasState())) {
            return Optional.of(current);
        }
        if (header == null) {
            return Optional.empty();
        }
        try {
            SfxBlockDataSnapshot snapshot = repository.loadChunk(
                    header.anchorKey().worldId(), header.anchorKey().x() >> 4, header.anchorKey().z() >> 4);
            for (SfxBlockInstanceRecord instance : snapshot.instances()) {
                if (dirtyInstanceIds.contains(instance.instanceId())) {
                    continue;
                }
                putInstanceRecord(instance);
                indexInstanceType(instance);
            }
            SfxBlockInstanceRecord materialized = instances.get(instanceId);
            if (materialized == null) {
                throw new IllegalStateException("SFX block instance is missing from its chunk record");
            }
            return Optional.of(materialized);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to lazy-load SFX block state for " + instanceId + ": " + exception.getMessage());
            throw new IllegalStateException("Failed to lazy-load SFX block state for " + instanceId, exception);
        }
    }

    public List<SfxAnchorRecord> anchorsInChunk(UUID worldId, int chunkX, int chunkZ) {
        if (worldId == null) {
            return List.of();
        }
        Set<SfxBlockAnchorKey> keys = anchorsByChunk.get(new ChunkIndexKey(worldId, chunkX, chunkZ));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<SfxAnchorRecord> results = new ArrayList<>(keys.size());
        for (SfxBlockAnchorKey key : keys) {
            SfxAnchorRecord anchor = anchors.get(key);
            if (anchor != null && anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                results.add(anchor);
            }
        }
        return results;
    }

    public List<SfxBlockInstanceRecord> instancesInChunk(UUID worldId, int chunkX, int chunkZ) {
        List<SfxAnchorRecord> chunkAnchors = anchorsInChunk(worldId, chunkX, chunkZ);
        if (chunkAnchors.isEmpty()) {
            return List.of();
        }
        Map<UUID, SfxBlockInstanceRecord> results = new HashMap<>();
        for (SfxAnchorRecord anchor : chunkAnchors) {
            SfxBlockInstanceRecord instance = findInstance(anchor.instanceId()).orElse(null);
            if (instance != null) {
                results.putIfAbsent(instance.instanceId(), instance);
            }
        }
        return new ArrayList<>(results.values());
    }

    public List<SfxAnchorRecord> anchorsInWorld(UUID worldId) {
        if (worldId == null) {
            return List.of();
        }
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (Map.Entry<ChunkIndexKey, Set<SfxBlockAnchorKey>> entry : anchorsByChunk.entrySet()) {
            if (!worldId.equals(entry.getKey().worldId())) {
                continue;
            }
            for (SfxBlockAnchorKey key : entry.getValue()) {
                SfxAnchorRecord anchor = anchors.get(key);
                if (anchor != null && anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                    results.add(anchor);
                }
            }
        }
        return results;
    }

    public List<SfxAnchorRecord> anchorsInLoadedWorld(World world) {
        if (world == null) {
            return List.of();
        }
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            results.addAll(anchorsInChunk(world.getUID(), chunk.getX(), chunk.getZ()));
        }
        return results;
    }

    public List<SfxBlockInstanceRecord> instancesOfType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return List.of();
        }
        Set<UUID> ids = instancesByType.get(typeId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SfxBlockInstanceRecord> results = new ArrayList<>(ids.size());
        for (UUID instanceId : ids) {
            SfxBlockInstanceRecord instance = findInstance(instanceId).orElse(null);
            if (instance != null) {
                SfxAnchorRecord anchor = anchors.get(instance.anchorKey());
                if (anchor != null && anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                    results.add(instance);
                }
            }
        }
        return results;
    }

    public synchronized void registerMaterialVariants(String typeId, Iterable<Material> materials) {
        if (typeId == null || typeId.isBlank() || materials == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (Material material : materials) {
            if (material != null && material.isBlock()) {
                keys.add(material.key().toString());
            }
        }
        if (!keys.isEmpty()) {
            String normalizedTypeId = typeId.trim().toLowerCase(java.util.Locale.ROOT);
            materialVariants.put(normalizedTypeId, Set.copyOf(keys));
            revalidateRegisteredVariants(normalizedTypeId, keys);
        }
    }

    private void revalidateRegisteredVariants(String typeId, Set<String> keys) {
        for (SfxAnchorRecord anchor : List.copyOf(anchors.values())) {
            if (anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                continue;
            }
            SfxBlockInstanceRecord instance = findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !typeId.equals(instance.typeId())) {
                continue;
            }
            SfxBlockAnchorKey key = anchor.key();
            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Material actual = world.getBlockAt(key.x(), key.y(), key.z()).getType();
            if (!actual.isAir() && keys.contains(actual.key().toString())) {
                markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.VALID);
            }
        }
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

    public List<SfxAnchorRecord> allAnchorsSnapshot() {
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (SfxAnchorRecord anchor : anchors.values()) {
            if (anchor.integrityState() == SfxBlockIntegrityState.VALID) {
                results.add(anchor);
            }
        }
        return results;
    }

    
    @Deprecated
    public List<SfxAnchorRecord> anchors() {
        return allAnchorsSnapshot();
    }

    public List<SfxAnchorRecord> anchorsNear(SfxBlockAnchorKey origin, int rangeX, int rangeZ) {
        if (origin == null) {
            return List.of();
        }
        int normalizedX = Math.max(0, rangeX);
        int normalizedZ = Math.max(0, rangeZ);
        int minChunkX = Math.floorDiv(origin.x() - normalizedX, 16);
        int maxChunkX = Math.floorDiv(origin.x() + normalizedX, 16);
        int minChunkZ = Math.floorDiv(origin.z() - normalizedZ, 16);
        int maxChunkZ = Math.floorDiv(origin.z() + normalizedZ, 16);
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<SfxBlockAnchorKey> keys = anchorsByChunk.get(
                        new ChunkIndexKey(origin.worldId(), chunkX, chunkZ));
                if (keys == null) {
                    continue;
                }
                for (SfxBlockAnchorKey key : keys) {
                    SfxAnchorRecord anchor = anchors.get(key);
                    if (anchor != null && anchor.integrityState() == SfxBlockIntegrityState.VALID
                            && Math.abs((long) key.x() - origin.x()) <= normalizedX
                            && Math.abs((long) key.z() - origin.z()) <= normalizedZ) {
                        results.add(anchor);
                    }
                }
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
            unindexAnchor(replaced.key());
            SfxBlockInstanceRecord replacedInstance = findInstance(replaced.instanceId()).orElse(null);
            unindexInstanceType(replacedInstance);
            instances.remove(replaced.instanceId());
            instanceHeaders.remove(replaced.instanceId());
            removeDirtyInstance(replaced.instanceId());
            pendingInstanceDeletes.put(replaced.instanceId(), replaced.key());
            dirtyChunkFor(replaced.key()).instanceDeletes.put(replaced.instanceId(), replaced.key());
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
        putInstanceRecord(instance);
        indexInstanceType(instance);
        anchors.put(key, anchor);
        indexAnchor(key);
        markDirty(anchor, instance);
        revision.incrementAndGet();
        notifyAnchorAdded(anchor);
        return instanceId;
    }

    public synchronized void updateEnergyPriorityDistance(UUID instanceId, int energyPriorityDistance) {
        SfxBlockInstanceRecord existing = findInstance(instanceId).orElse(null);
        if (existing == null || existing.energyPriorityDistance() == energyPriorityDistance) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withEnergyPriorityDistance(energyPriorityDistance, Instant.now().toEpochMilli());
        putInstanceRecord(updated);
        markDirty(updated);
    }

    public synchronized void updateInstanceState(UUID instanceId, byte[] stateBlob, SfxBlockLifecycleState lifecycleState) {
        SfxBlockInstanceRecord existing = findInstance(instanceId).orElse(null);
        if (existing == null) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withState(stateBlob, lifecycleState, Instant.now().toEpochMilli());
        putInstanceRecord(updated);
        markDirty(updated);
    }

    public synchronized void updateInstanceState(UUID instanceId, byte[] stateBlob,
                                                 SfxBlockLifecycleState lifecycleState, int schemaVersion) {
        SfxBlockInstanceRecord existing = findInstance(instanceId).orElse(null);
        if (existing == null) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withState(stateBlob, lifecycleState,
                Math.max(1, schemaVersion), Instant.now().toEpochMilli());
        putInstanceRecord(updated);
        markDirty(updated);
    }

    
    public synchronized Optional<SfxBlockInstanceRecord> updateInstanceAtomic(
            UUID instanceId, java.util.function.UnaryOperator<SfxBlockInstanceRecord> update) {
        SfxBlockInstanceRecord existing = findInstance(instanceId).orElse(null);
        if (existing == null || update == null) return Optional.empty();
        SfxBlockInstanceRecord changed = Objects.requireNonNull(update.apply(existing), "updated instance");
        if (!existing.instanceId().equals(changed.instanceId()) || !existing.anchorKey().equals(changed.anchorKey())) {
            throw new IllegalArgumentException("Atomic state update cannot change instance identity or anchor");
        }
        putInstanceRecord(changed);
        markDirty(changed);
        return Optional.of(changed);
    }

    public synchronized boolean replaceInstanceType(UUID instanceId, String typeId, Material material,
                                                     byte[] stateBlob, int schemaVersion) {
        SfxBlockInstanceRecord existing = findInstance(instanceId).orElse(null);
        SfxAnchorRecord anchor = existing == null ? null : anchors.get(existing.anchorKey());
        if (existing == null || anchor == null || typeId == null || typeId.isBlank()
                || material == null || !material.isBlock()) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        SfxBlockInstanceRecord updatedInstance = existing.withTypeAndState(typeId, stateBlob, schemaVersion, now);
        SfxAnchorRecord updatedAnchor = anchor.withMaterial(material.key().toString(), now);
        unindexInstanceType(existing);
        putInstanceRecord(updatedInstance);
        indexInstanceType(updatedInstance);
        anchors.put(anchor.key(), updatedAnchor);
        markDirty(updatedAnchor, updatedInstance);
        revision.incrementAndGet();
        notifyAnchorUpdated(updatedAnchor);
        return true;
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
        notifyAnchorUpdated(updated);
    }

    public synchronized void unregisterAt(Location location) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.remove(key);
        if (anchor == null) {
            return;
        }
        UUID instanceId = anchor.instanceId();
        unindexAnchor(key);
        unindexInstanceType(findInstance(instanceId).orElse(null));
        instances.remove(instanceId);
        instanceHeaders.remove(instanceId);
        removeDirtyAnchor(key);
        removeDirtyInstance(instanceId);
        pendingAnchorDeletes.add(key);
        pendingInstanceDeletes.put(instanceId, key);
        DirtyChunkState dirty = dirtyChunkFor(key);
        dirty.anchorDeletes.add(key);
        dirty.instanceDeletes.put(instanceId, key);
        revision.incrementAndGet();
        notifyAnchorRemoved(key);
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
        flushAllBlocking();
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
                clearPersistedBatch(batch);
            }
        }
    }

    private void clearPersistedBatch(PersistenceBatch batch) {
        Map<SfxBlockAnchorKey, SfxAnchorRecord> persistedAnchors = new HashMap<>();
        for (SfxAnchorRecord anchor : batch.anchorUpserts()) {
            persistedAnchors.put(anchor.key(), anchor);
        }
        for (SfxBlockAnchorKey key : batch.dirtyAnchorKeys()) {
            if (Objects.equals(anchors.get(key), persistedAnchors.get(key))) {
                removeDirtyAnchor(key);
            }
        }

        Map<UUID, SfxBlockInstanceRecord> persistedInstances = new HashMap<>();
        for (SfxBlockInstanceRecord instance : batch.instanceUpserts()) {
            persistedInstances.put(instance.instanceId(), instance);
        }
        for (UUID instanceId : batch.dirtyInstanceIds()) {
            if (Objects.equals(findInstance(instanceId).orElse(null), persistedInstances.get(instanceId))) {
                removeDirtyInstance(instanceId);
                evictDetachedInstanceIfClean(instanceId);
            }
        }

        for (SfxBlockAnchorKey key : batch.anchorDeleteKeys()) {
            if (!anchors.containsKey(key)) {
                removePendingAnchorDelete(key);
            }
        }
        for (UUID instanceId : batch.instanceDeleteIds()) {
            if (findInstance(instanceId).isEmpty()
                    && Objects.equals(pendingInstanceDeletes.get(instanceId),
                    batch.instanceDeleteAnchorKeys().get(instanceId))) {
                removePendingInstanceDelete(instanceId, batch.instanceDeleteAnchorKeys().get(instanceId));
            }
        }
    }

    public void flushChunk(World world, int chunkX, int chunkZ) {
        requestChunkFlushAsync(world, chunkX, chunkZ);
    }

    
    public synchronized void attachChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        ChunkIndexKey chunkKey = new ChunkIndexKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!loadedChunks.add(chunkKey)) {
            return;
        }
        try {
            SfxBlockDataSnapshot snapshot = repository.loadChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
            for (SfxAnchorRecord anchor : snapshot.anchors()) {
                if (!dirtyAnchorKeys.contains(anchor.key())) {
                    anchors.put(anchor.key(), anchor);
                }
                indexAnchor(anchor.key());
            }
            for (SfxBlockInstanceRecord instance : snapshot.instances()) {
                SfxBlockInstanceRecord current = instances.get(instance.instanceId());
                if (current == null || !dirtyInstanceIds.contains(instance.instanceId())) {
                    putInstanceRecord(instance);
                    indexInstanceType(instance);
                } else {
                    instanceHeaders.put(instance.instanceId(), toHeader(current));
                    indexInstanceType(current);
                }
            }
        } catch (Exception exception) {
            loadedChunks.remove(chunkKey);
            throw new IllegalStateException("Failed to load SFX block runtime for " + chunkKey, exception);
        }
    }

    
    public synchronized void detachChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        ChunkIndexKey chunkKey = new ChunkIndexKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!loadedChunks.contains(chunkKey) || !flushChunkBlocking(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            return;
        }
        loadedChunks.remove(chunkKey);
        Set<SfxBlockAnchorKey> chunkAnchorKeys = anchorsByChunk.getOrDefault(chunkKey, Set.of());
        for (SfxBlockAnchorKey anchorKey : chunkAnchorKeys) {
            SfxAnchorRecord anchor = anchors.get(anchorKey);
            if (anchor == null) {
                continue;
            }
            UUID instanceId = anchor.instanceId();
            if (hasLoadedAnchor(instanceId)) {
                continue;
            }
            SfxBlockInstanceRecord current = instances.remove(instanceId);
            if (current != null) {
                instanceHeaders.put(instanceId, toHeader(current));
            }
        }
    }

    private boolean flushChunkBlocking(World world, int chunkX, int chunkZ) {
        PersistenceBatch batch = snapshotDirtyChunk(world.getUID(), chunkX, chunkZ);
        if (batch.isEmpty()) {
            try {
                repository.awaitPendingWrites();
                return true;
            } catch (Exception exception) {
                restoreDirtyBatch(batch);
                plugin.getLogger().warning("Failed to drain pending SFX block chunk writes "
                        + chunkX + "," + chunkZ + ": " + exception.getMessage());
                return false;
            }
        }
        try {
            repository.persistChanges(batch.anchorUpserts(), batch.instanceUpserts(), batch.anchorDeletes(), batch.instanceDeletes());
            repository.awaitPendingWrites();
            clearPersistedBatch(batch);
            return true;
        } catch (Exception exception) {
            restoreDirtyBatch(batch);
            plugin.getLogger().warning("Failed to flush SFX block chunk " + chunkX + "," + chunkZ + ": " + exception.getMessage());
            return false;
        }
    }

    private void putInstanceRecord(SfxBlockInstanceRecord instance) {
        if (instance == null) {
            return;
        }
        instanceHeaders.put(instance.instanceId(), toHeader(instance));
        instances.put(instance.instanceId(), instance);
    }

    private SfxBlockInstanceRecord toHeader(SfxBlockInstanceRecord instance) {
        return new SfxBlockInstanceRecord(
                instance.instanceId(), instance.typeId(), instance.anchorKey(), instance.lifecycleState(),
                instance.version(), instance.ownerId(), new byte[0], instance.updatedAt(), instance.energyPriorityDistance());
    }

    private void evictDetachedInstanceIfClean(UUID instanceId) {
        SfxBlockInstanceRecord current = instances.get(instanceId);
        if (current == null || dirtyInstanceIds.contains(instanceId)
                || hasLoadedAnchor(instanceId)) {
            return;
        }
        instances.remove(instanceId, current);
        instanceHeaders.put(instanceId, toHeader(current));
    }

    private boolean hasLoadedAnchor(UUID instanceId) {
        for (SfxAnchorRecord anchor : anchorsForInstance(instanceId)) {
            if (loadedChunks.contains(ChunkIndexKey.of(anchor.key()))) {
                return true;
            }
        }
        return false;
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
        for (Chunk chunk : world.getLoadedChunks()) {
            reconcileChunk(world, chunk.getX(), chunk.getZ());
        }
    }

    public void reconcileChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (SfxAnchorRecord anchor : anchorsInChunk(world.getUID(), chunkX, chunkZ)) {
            SfxBlockAnchorKey key = anchor.key();
            Location location = new Location(world, key.x(), key.y(), key.z());
            runtime.executeAt(location, () -> {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    reconcileAnchor(world, anchor);
                }
            });
        }
    }

    @Override
    public synchronized void shutdown() {
        shuttingDown = true;
        flushAllBlocking();
        repository.close();
        anchors.clear();
        instances.clear();
        anchorsByChunk.clear();
        instancesByType.clear();
        dirtyByChunk.clear();
        dirtyInstanceChunks.clear();
        dirtyAnchorKeys.clear();
        dirtyInstanceIds.clear();
        pendingAnchorDeletes.clear();
        pendingInstanceDeletes.clear();
        loadedChunks.clear();
        instanceHeaders.clear();
        anchorChangeListeners.clear();
    }

    public int anchorCount() {
        return anchors.size();
    }

    public int instanceCount() {
        return instanceHeaders.size();
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
        SfxBlockInstanceRecord instance = findInstance(anchor.instanceId()).orElse(null);
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
        Set<String> registered = materialVariants.get(id);
        if (registered != null && registered.contains(actual.key().toString())) {
            return true;
        }
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
        notifyAnchorUpdated(updated);
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
            markDirtyAnchorKey(anchor.key());
        }
    }

    private void markDirty(SfxBlockInstanceRecord instance) {
        if (instance != null) {
            dirtyInstanceIds.add(instance.instanceId());
            dirtyInstanceChunks.put(instance.instanceId(), ChunkIndexKey.of(instance.anchorKey()));
            dirtyChunkFor(instance.anchorKey()).instanceIds.add(instance.instanceId());
        }
    }

    private void markDirtyAnchorKey(SfxBlockAnchorKey key) {
        if (key == null) {
            return;
        }
        dirtyAnchorKeys.add(key);
        dirtyChunkFor(key).anchorKeys.add(key);
    }

    private void removeDirtyAnchor(SfxBlockAnchorKey key) {
        if (key == null) {
            return;
        }
        dirtyAnchorKeys.remove(key);
        ChunkIndexKey chunkKey = ChunkIndexKey.of(key);
        DirtyChunkState dirty = dirtyByChunk.get(chunkKey);
        if (dirty != null) {
            dirty.anchorKeys.remove(key);
            dirty.anchorDeletes.remove(key);
            cleanupDirtyChunk(chunkKey, dirty);
        }
    }

    private void removeDirtyInstance(UUID instanceId) {
        if (instanceId == null) {
            return;
        }
        dirtyInstanceIds.remove(instanceId);
        ChunkIndexKey chunkKey = dirtyInstanceChunks.remove(instanceId);
        if (chunkKey == null) {
            return;
        }
        DirtyChunkState dirty = dirtyByChunk.get(chunkKey);
        if (dirty != null) {
            dirty.instanceIds.remove(instanceId);
            cleanupDirtyChunk(chunkKey, dirty);
        }
    }

    private DirtyChunkState dirtyChunkFor(SfxBlockAnchorKey key) {
        return dirtyByChunk.computeIfAbsent(ChunkIndexKey.of(key), ignored -> new DirtyChunkState());
    }

    private void cleanupDirtyChunk(ChunkIndexKey chunkKey, DirtyChunkState dirty) {
        if (dirty.isEmpty()) {
            dirtyByChunk.remove(chunkKey, dirty);
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

        DirtyChunkState scopedDirty = selector == null ? null : dirtyByChunk.get(selector.indexKey());
        Set<SfxBlockAnchorKey> candidateAnchorKeys = selector == null
                ? new HashSet<>(dirtyAnchorKeys)
                : scopedDirty == null ? Set.of() : new HashSet<>(scopedDirty.anchorKeys);
        Set<UUID> candidateInstanceIds = selector == null
                ? new HashSet<>(dirtyInstanceIds)
                : scopedDirty == null ? Set.of() : new HashSet<>(scopedDirty.instanceIds);
        Set<SfxBlockAnchorKey> candidateAnchorDeletes = selector == null
                ? new HashSet<>(pendingAnchorDeletes)
                : scopedDirty == null ? Set.of() : new HashSet<>(scopedDirty.anchorDeletes);
        Map<UUID, SfxBlockAnchorKey> candidateInstanceDeletes = selector == null
                ? new HashMap<>(pendingInstanceDeletes)
                : scopedDirty == null ? Map.of() : new HashMap<>(scopedDirty.instanceDeletes);

        for (SfxBlockAnchorKey key : candidateAnchorKeys) {
            if (!matches(selector, key)) {
                continue;
            }
            SfxAnchorRecord anchor = anchors.get(key);
            if (anchor == null) {
                continue;
            }
            dirtyAnchors.add(key);
            anchorUpserts.put(key, anchor);
            SfxBlockInstanceRecord instance = findInstance(anchor.instanceId()).orElse(null);
            if (instance != null) {
                instanceUpserts.put(instance.instanceId(), instance);
            }
        }

        for (UUID instanceId : candidateInstanceIds) {
            SfxBlockInstanceRecord instance = findInstance(instanceId).orElse(null);
            if (instance == null || !matches(selector, instance.anchorKey())) {
                continue;
            }
            dirtyInstances.add(instanceId);
            instanceUpserts.put(instanceId, instance);
        }

        for (SfxBlockAnchorKey key : candidateAnchorDeletes) {
            if (matches(selector, key)) {
                anchorDeletes.add(key);
            }
        }

        for (Map.Entry<UUID, SfxBlockAnchorKey> entry : candidateInstanceDeletes.entrySet()) {
            if (matches(selector, entry.getValue())) {
                instanceDeletes.add(entry.getKey());
            }
        }

        Map<UUID, SfxBlockAnchorKey> instanceDeleteAnchorKeys = new HashMap<>();
        for (UUID instanceId : instanceDeletes) {
            SfxBlockAnchorKey deleteKey = pendingInstanceDeletes.remove(instanceId);
            if (deleteKey != null) {
                instanceDeleteAnchorKeys.put(instanceId, deleteKey);
            }
        }
        for (SfxBlockAnchorKey key : anchorDeletes) {
            pendingAnchorDeletes.remove(key);
        }
        clearDirtyMarkers(dirtyAnchors, dirtyInstances, anchorDeletes, instanceDeleteAnchorKeys);

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

    private void clearDirtyMarkers(Set<SfxBlockAnchorKey> anchorKeys, Set<UUID> instanceIds,
                                   Set<SfxBlockAnchorKey> anchorDeletes,
                                   Map<UUID, SfxBlockAnchorKey> instanceDeleteAnchorKeys) {
        for (SfxBlockAnchorKey key : anchorKeys) {
            removeDirtyAnchor(key);
        }
        for (UUID instanceId : instanceIds) {
            removeDirtyInstance(instanceId);
        }
        for (SfxBlockAnchorKey key : anchorDeletes) {
            DirtyChunkState dirty = dirtyByChunk.get(ChunkIndexKey.of(key));
            if (dirty != null) {
                dirty.anchorDeletes.remove(key);
                cleanupDirtyChunk(ChunkIndexKey.of(key), dirty);
            }
        }
        for (Map.Entry<UUID, SfxBlockAnchorKey> entry : instanceDeleteAnchorKeys.entrySet()) {
            DirtyChunkState dirty = dirtyByChunk.get(ChunkIndexKey.of(entry.getValue()));
            if (dirty != null) {
                dirty.instanceDeletes.remove(entry.getKey());
                cleanupDirtyChunk(ChunkIndexKey.of(entry.getValue()), dirty);
            }
        }
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
            SfxBlockInstanceRecord instance = findInstance(instanceId).orElse(null);
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
                synchronized (this) {
                    clearPersistedBatch(batch);
                }
                return;
            }
            plugin.getLogger().warning("Failed to persist SFX block data batch: " + throwable.getMessage());
            restoreDirtyBatch(batch);
        });
    }

    private synchronized void restoreDirtyBatch(PersistenceBatch batch) {
        for (SfxBlockAnchorKey key : batch.dirtyAnchorKeys()) {
            markDirtyAnchorKey(key);
        }
        for (UUID instanceId : batch.dirtyInstanceIds()) {
            SfxBlockInstanceRecord instance = findInstance(instanceId).orElse(null);
            if (instance != null) {
                markDirty(instance);
            }
        }
        for (SfxBlockAnchorKey key : batch.anchorDeleteKeys()) {
            pendingAnchorDeletes.add(key);
            dirtyChunkFor(key).anchorDeletes.add(key);
        }
        for (UUID instanceId : batch.instanceDeleteIds()) {
            SfxBlockAnchorKey key = batch.instanceDeleteAnchorKeys().get(instanceId);
            if (key != null) {
                pendingInstanceDeletes.put(instanceId, key);
                dirtyChunkFor(key).instanceDeletes.put(instanceId, key);
            }
        }
    }

    private void removePendingAnchorDelete(SfxBlockAnchorKey key) {
        pendingAnchorDeletes.remove(key);
        DirtyChunkState dirty = dirtyByChunk.get(ChunkIndexKey.of(key));
        if (dirty != null) {
            dirty.anchorDeletes.remove(key);
            cleanupDirtyChunk(ChunkIndexKey.of(key), dirty);
        }
    }

    private void removePendingInstanceDelete(UUID instanceId, SfxBlockAnchorKey key) {
        pendingInstanceDeletes.remove(instanceId, key);
        if (key == null) {
            return;
        }
        DirtyChunkState dirty = dirtyByChunk.get(ChunkIndexKey.of(key));
        if (dirty != null) {
            dirty.instanceDeletes.remove(instanceId);
            cleanupDirtyChunk(ChunkIndexKey.of(key), dirty);
        }
    }

    private record ChunkSelector(UUID worldId, int chunkX, int chunkZ) {
        boolean matches(SfxBlockAnchorKey key) {
            return key.worldId().equals(worldId) && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ;
        }

        ChunkIndexKey indexKey() {
            return new ChunkIndexKey(worldId, chunkX, chunkZ);
        }
    }

    private void indexAnchor(SfxBlockAnchorKey key) {
        anchorsByChunk.computeIfAbsent(ChunkIndexKey.of(key), ignored -> ConcurrentHashMap.newKeySet())
                .add(key);
    }

    private void unindexAnchor(SfxBlockAnchorKey key) {
        ChunkIndexKey chunk = ChunkIndexKey.of(key);
        Set<SfxBlockAnchorKey> keys = anchorsByChunk.get(chunk);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            anchorsByChunk.remove(chunk, keys);
        }
    }

    private void indexInstanceType(SfxBlockInstanceRecord instance) {
        if (instance == null || instance.typeId() == null) {
            return;
        }
        instancesByType.computeIfAbsent(instance.typeId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(instance.instanceId());
    }

    private void unindexInstanceType(SfxBlockInstanceRecord instance) {
        if (instance == null || instance.typeId() == null) {
            return;
        }
        Set<UUID> ids = instancesByType.get(instance.typeId());
        if (ids == null) {
            return;
        }
        ids.remove(instance.instanceId());
        if (ids.isEmpty()) {
            instancesByType.remove(instance.typeId(), ids);
        }
    }

    private record ChunkIndexKey(UUID worldId, int chunkX, int chunkZ) {
        static ChunkIndexKey of(SfxBlockAnchorKey key) {
            return new ChunkIndexKey(key.worldId(), key.x() >> 4, key.z() >> 4);
        }
    }

    private static final class DirtyChunkState {
        private final Set<SfxBlockAnchorKey> anchorKeys = ConcurrentHashMap.newKeySet();
        private final Set<UUID> instanceIds = ConcurrentHashMap.newKeySet();
        private final Set<SfxBlockAnchorKey> anchorDeletes = ConcurrentHashMap.newKeySet();
        private final Map<UUID, SfxBlockAnchorKey> instanceDeletes = new ConcurrentHashMap<>();

        private boolean isEmpty() {
            return anchorKeys.isEmpty() && instanceIds.isEmpty()
                    && anchorDeletes.isEmpty() && instanceDeletes.isEmpty();
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
