package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxBlockDataService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataRepository repository;
    private final Map<SfxBlockAnchorKey, SfxAnchorRecord> anchors = new ConcurrentHashMap<>();
    private final Map<UUID, SfxBlockInstanceRecord> instances = new ConcurrentHashMap<>();

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
        snapshot.anchors().forEach(anchor -> anchors.put(anchor.key(), anchor));
        snapshot.instances().forEach(instance -> instances.put(instance.instanceId(), instance));
    }

    public Optional<SfxAnchorRecord> findAnchor(Location location) {
        return Optional.ofNullable(anchors.get(SfxBlockAnchorKey.fromLocation(location)));
    }

    public Optional<SfxBlockInstanceRecord> findInstance(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public List<SfxAnchorRecord> anchorsForInstance(UUID instanceId) {
        List<SfxAnchorRecord> results = new ArrayList<>();
        for (SfxAnchorRecord anchor : anchors.values()) {
            if (anchor.instanceId().equals(instanceId)) {
                results.add(anchor);
            }
        }
        return results;
    }

    public UUID registerSingleBlock(String typeId, Location location, Material material, UUID ownerId) {
        long now = Instant.now().toEpochMilli();
        UUID instanceId = UUID.randomUUID();
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxBlockInstanceRecord instance = new SfxBlockInstanceRecord(
                instanceId,
                typeId,
                key,
                SfxBlockLifecycleState.IDLE,
                1,
                ownerId,
                new byte[0],
                now);
        SfxAnchorRecord anchor = new SfxAnchorRecord(
                key,
                material.key().toString(),
                instanceId,
                SfxBlockAnchorKind.SINGLE_BLOCK,
                SfxBlockIntegrityState.VALID,
                now);
        instances.put(instanceId, instance);
        anchors.put(key, anchor);
        persist(instance, anchor);
        return instanceId;
    }

    public void updateInstanceState(UUID instanceId, byte[] stateBlob, SfxBlockLifecycleState lifecycleState) {
        SfxBlockInstanceRecord existing = instances.get(instanceId);
        if (existing == null) {
            return;
        }
        SfxBlockInstanceRecord updated = existing.withState(stateBlob, lifecycleState, Instant.now().toEpochMilli());
        instances.put(instanceId, updated);
        runtime.executeAsync(() -> saveInstance(updated));
    }

    public void markAnchorIntegrity(Location location, SfxBlockIntegrityState integrityState) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord existing = anchors.get(key);
        if (existing == null) {
            return;
        }
        SfxAnchorRecord updated = existing.withIntegrity(integrityState, Instant.now().toEpochMilli());
        anchors.put(key, updated);
        runtime.executeAsync(() -> saveAnchor(updated));
    }

    public void unregisterAt(Location location) {
        SfxBlockAnchorKey key = SfxBlockAnchorKey.fromLocation(location);
        SfxAnchorRecord anchor = anchors.remove(key);
        if (anchor == null) {
            return;
        }
        UUID instanceId = anchor.instanceId();
        instances.remove(instanceId);
        runtime.executeAsync(() -> {
            try {
                repository.deleteAnchor(key);
                repository.deleteInstance(instanceId);
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to delete SFX block instance " + instanceId + ": " + exception.getMessage());
            }
        });
    }

    public void shutdown() {
        repository.close();
        anchors.clear();
        instances.clear();
    }

    public int anchorCount() {
        return anchors.size();
    }

    public int instanceCount() {
        return instances.size();
    }

    private void persist(SfxBlockInstanceRecord instance, SfxAnchorRecord anchor) {
        runtime.executeAsync(() -> {
            saveInstance(instance);
            saveAnchor(anchor);
        });
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
