package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBlockDataService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataRepository repository;
    private final Map<SfxBlockAnchorKey, SfxAnchorRecord> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, SfxBlockInstanceRecord> instances = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Void>> pendingWrites = ConcurrentHashMap.newKeySet();
    private final Set<SfxBlockAnchorKey> pendingAnchorDeletes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingInstanceDeletes = ConcurrentHashMap.newKeySet();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean shuttingDown;

    public SfxBlockDataService(JavaPlugin plugin, SfxRuntime runtime, SfxBlockDataRepository repository) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.repository = repository;
    }

    public void initialize() throws Exception {
        repository.initialize();
        SfxBlockDataSnapshot snapshot = repository.loadAll();
        anchors.clear();
        instances.clear();
        pendingAnchorDeletes.clear();
        pendingInstanceDeletes.clear();
        snapshot.anchors().forEach(anchor -> anchors.put(anchor.key(), anchor));
        snapshot.instances().forEach(instance -> instances.put(instance.instanceId(), instance));
        revision.incrementAndGet();
        reconcileLoadedAnchors();
    }

    public Optional<SfxAnchorRecord> findAnchor(Location location) {
        if (location == null) {
            return Optional.empty();
        }
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.get(key);
        if (anchor == null) {
            return Optional.empty();
        }
        if (anchor.integrityState() != SfxBlockIntegrityState.VALID) {
            return Optional.empty();
        }
        if (!isAnchorMaterialValid(location, anchor)) {
            markAnchorIntegritySync(key, anchor, SfxBlockIntegrityState.INVALID);
            return Optional.empty();
        }
        return Optional.of(anchor);
    }


    public Optional<SfxAnchorRecord> findAnchor(SfxBlockAnchorKey key) {
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

    public UUID registerSingleBlock(String typeId, Location location, Material material, UUID ownerId, int energyPriorityDistance) {
        long now = Instant.now().toEpochMilli();
        UUID instanceId = UUID.randomUUID();
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord replaced = anchors.remove(key);
        if (replaced != null) {
            instances.remove(replaced.instanceId());
            pendingInstanceDeletes.add(replaced.instanceId());
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
        revision.incrementAndGet();
        return instanceId;
    }

    public void updateEnergyPriorityDistance(UUID instanceId, int energyPriorityDistance) {
        SfxBlockInstanceRecord existing = instances.get(instanceId);
        if (existing == null || existing.energyPriorityDistance() == energyPriorityDistance) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withEnergyPriorityDistance(energyPriorityDistance, Instant.now().toEpochMilli());
        instances.put(instanceId, updated);
    }

    public void updateInstanceState(UUID instanceId, byte[] stateBlob, SfxBlockLifecycleState lifecycleState) {
        SfxBlockInstanceRecord existing = instances.get(instanceId);
        if (existing == null) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withState(stateBlob, lifecycleState, Instant.now().toEpochMilli());
        instances.put(instanceId, updated);
        enqueueWrite(() -> saveInstance(updated));
    }

    public void markAnchorIntegrity(Location location, SfxBlockIntegrityState integrityState) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord existing = anchors.get(key);
        if (existing == null) {
            return;
        }
        SfxAnchorRecord updated = updateAnchorIntegrityInMemory(existing, integrityState);
        revision.incrementAndGet();
        enqueueWrite(() -> saveAnchor(updated));
    }

    public void unregisterAt(Location location) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.remove(key);
        if (anchor == null) {
            return;
        }
        UUID instanceId = anchor.instanceId();
        instances.remove(instanceId);
        pendingAnchorDeletes.add(key);
        pendingInstanceDeletes.add(instanceId);
        revision.incrementAndGet();
    }

    public void flushNow() {
        awaitPendingWrites();
        flushDeletes();
        for (SfxBlockInstanceRecord instance : instances.values()) {
            saveInstance(instance);
        }
        for (SfxAnchorRecord anchor : anchors.values()) {
            saveAnchor(anchor);
        }
        flushDeletes();
        awaitPendingWrites();
    }

    public void flushChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        awaitPendingWrites();
        for (SfxAnchorRecord anchor : anchors.values()) {
            SfxBlockAnchorKey key = anchor.key();
            if (!key.worldId().equals(world.getUID()) || (key.x() >> 4) != chunkX || (key.z() >> 4) != chunkZ) {
                continue;
            }
            saveAnchor(anchor);
            SfxBlockInstanceRecord instance = instances.get(anchor.instanceId());
            if (instance != null) {
                saveInstance(instance);
            }
        }
        flushDeletes();
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
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            reconcileAnchor(world, anchor);
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
            reconcileAnchor(world, anchor);
        }
    }

    public void shutdown() {
        shuttingDown = true;
        flushNow();
        repository.close();
        anchors.clear();
        instances.clear();
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
        return actual.key().toString().equals(anchor.materialKey());
    }

    private void markAnchorIntegritySync(SfxBlockAnchorKey key, SfxAnchorRecord existing, SfxBlockIntegrityState integrityState) {
        if (existing.integrityState() == integrityState) {
            return;
        }
        SfxAnchorRecord updated = updateAnchorIntegrityInMemory(existing, integrityState);
        revision.incrementAndGet();
        saveAnchor(updated);
        if (integrityState == SfxBlockIntegrityState.INVALID) {
            plugin.getLogger().warning("Marked invalid SFX block anchor at " + key + " because the world block no longer matches " + existing.materialKey());
        }
    }

    private SfxAnchorRecord updateAnchorIntegrityInMemory(SfxAnchorRecord existing, SfxBlockIntegrityState integrityState) {
        SfxAnchorRecord updated = existing.withIntegrity(integrityState, Instant.now().toEpochMilli());
        anchors.put(existing.key(), updated);
        return updated;
    }

    private void enqueueWrite(Runnable write) {
        if (shuttingDown) {
            write.run();
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingWrites.add(future);
        runtime.executeAsync(() -> {
            try {
                write.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
                plugin.getLogger().warning("Failed to execute SFX block data write: " + throwable.getMessage());
            } finally {
                pendingWrites.remove(future);
            }
        });
    }

    private void awaitPendingWrites() {
        CompletableFuture<?>[] futures = pendingWrites.toArray(CompletableFuture[]::new);
        if (futures.length == 0) {
            return;
        }
        try {
            CompletableFuture.allOf(futures).get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Timed out while flushing SFX block data writes: " + exception.getMessage());
        }
    }

    private void flushDeletes() {
        for (SfxBlockAnchorKey key : new ArrayList<>(pendingAnchorDeletes)) {
            try {
                repository.deleteAnchor(key);
                pendingAnchorDeletes.remove(key);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to delete SFX anchor " + key + ": " + exception.getMessage());
            }
        }
        for (UUID instanceId : new ArrayList<>(pendingInstanceDeletes)) {
            try {
                repository.deleteInstance(instanceId);
                pendingInstanceDeletes.remove(instanceId);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to delete SFX block instance " + instanceId + ": " + exception.getMessage());
            }
        }
    }

    private void saveInstance(SfxBlockInstanceRecord instance) {
        try {
            repository.upsertInstance(instance);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save SFX block instance " + instance.instanceId() + ": " + exception.getMessage());
        }
    }

    private void saveAnchor(SfxAnchorRecord anchor) {
        try {
            repository.upsertAnchor(anchor);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to save SFX anchor " + anchor.key() + ": " + exception.getMessage());
        }
    }
}
