package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockEventContext;
import cc.theends6.sfx.api.block.SfxBlockAnchorKey;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockLifecycleState;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.api.randomtick.SfxRandomTickContext;
import cc.theends6.sfx.api.randomtick.SfxRandomTickType;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxAddonRandomTickService implements SfxBlockDataService.AnchorChangeListener {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataService blockData;
    private final SfxAddonManager addons;
    private final Map<String, Set<SfxBlockAnchorKey>> loadedByType = new ConcurrentHashMap<>();
    private final Set<SfxBlockAnchorKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> quarantined = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    public SfxAddonRandomTickService(JavaPlugin plugin, SfxRuntime runtime,
                                     SfxBlockDataService blockData, SfxAddonManager addons) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.blockData = blockData;
        this.addons = addons;
        blockData.addAnchorChangeListener(this);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        rebuildLoadedIndex();
        scheduleNext();
    }

    public synchronized void shutdown() {
        running = false;
        blockData.removeAnchorChangeListener(this);
        loadedByType.clear();
        inFlight.clear();
        quarantined.clear();
    }

    public void onChunkLoad(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (SfxAnchorRecord anchor : blockData.anchorsInChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            addAnchor(anchor);
        }
    }

    public void onChunkUnload(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        removeChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    @Override
    public void onAnchorAdded(SfxAnchorRecord anchor) {
        refreshAnchor(anchor == null ? null : anchor.key());
    }

    @Override
    public void onAnchorUpdated(SfxAnchorRecord anchor) {
        refreshAnchor(anchor == null ? null : anchor.key());
    }

    @Override
    public void onAnchorRemoved(SfxBlockAnchorKey key) {
        removeKey(key);
    }

    private void scheduleNext() {
        if (!running) return;
        runtime.executeGlobalLater(1L, () -> {
            if (!running) return;
            tick();
            scheduleNext();
        });
    }

    private void tick() {
        Collection<SfxRandomTickType<?>> definitions = addons.randomTickTypes();
        for (SfxRandomTickType<?> definition : definitions) {
            Set<SfxBlockAnchorKey> candidates = loadedByType.getOrDefault(definition.blockTypeId(), Set.of());
            if (candidates.isEmpty()) continue;
            Map<World, List<SfxBlockAnchorKey>> byWorld = new LinkedHashMap<>();
            for (SfxBlockAnchorKey candidate : candidates) {
                World world = Bukkit.getWorld(candidate.worldId());
                if (world != null && world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4)) {
                    byWorld.computeIfAbsent(world, ignored -> new ArrayList<>()).add(candidate);
                }
            }
            for (Map.Entry<World, List<SfxBlockAnchorKey>> entry : byWorld.entrySet()) {
                int speed = randomTickSpeed(entry.getKey());
                if (definition.affectedByGameRule() && speed <= 0) continue;
                int attempts = Math.min(definition.perTickBudget(), Math.max(1,
                        (int) Math.round((definition.affectedByGameRule() ? speed : 1) * definition.weight())));
                for (int attempt = 0; attempt < attempts; attempt++) {
                    List<SfxBlockAnchorKey> worldCandidates = entry.getValue();
                    SfxBlockAnchorKey selected = worldCandidates.get(ThreadLocalRandom.current().nextInt(worldCandidates.size()));
                    dispatch(definition, selected, entry.getKey(), speed);
                }
            }
        }
    }

    private void dispatch(SfxRandomTickType<?> definition, SfxBlockAnchorKey key, World world, int speed) {
        if (!inFlight.add(key)) return;
        Location location = new Location(world, key.x(), key.y(), key.z());
        runtime.executeAt(location, () -> {
            try {
                if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return;
                SfxAnchorRecord currentAnchor = blockData.findAnchorAndValidate(location).orElse(null);
                if (currentAnchor == null || !key.equals(currentAnchor.key())) return;
                SfxBlockInstanceRecord instance = blockData.findInstance(currentAnchor.instanceId()).orElse(null);
                if (instance == null || !definition.blockTypeId().equals(instance.typeId())) return;
                if (instance.lifecycleState() == SfxBlockLifecycleState.INVALID) return;
                if (quarantined.contains(instance.instanceId())) return;
                SfxBlockType<?> blockType = addons.blockType(instance.typeId()).orElse(null);
                if (blockType == null) return;
                try {
                    invoke(definition, blockType, instance, location, speed);
                } catch (RuntimeException exception) {
                    quarantined.add(instance.instanceId());
                    blockData.updateInstanceState(instance.instanceId(), instance.stateBlob(),
                            SfxBlockLifecycleState.INVALID, instance.version());
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Quarantined random-tick state for " + instance.typeId() + " at " + location, exception);
                }
            } finally {
                inFlight.remove(key);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void invoke(SfxRandomTickType definition, SfxBlockType blockType,
                        SfxBlockInstanceRecord instance, Location location, int speed) {
        Object state = !instance.hasState() ? blockType.initialState().get()
                : blockType.stateSchema().decode(instance.version(), instance.stateBlob());
        MutableContext context = new MutableContext(instance.instanceId(), instance.typeId(), location, state, speed);
        definition.handler().accept(context);
        byte[] encoded = blockType.stateSchema().codec().encode(context.state());
        blockData.updateInstanceState(instance.instanceId(), encoded, instance.lifecycleState(),
                blockType.stateSchema().version());
    }

    private int randomTickSpeed(World world) {
        Integer value = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
        return value == null ? 3 : Math.max(0, value);
    }

    private synchronized void rebuildLoadedIndex() {
        loadedByType.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                onChunkLoad(chunk);
            }
        }
    }

    private void refreshAnchor(SfxBlockAnchorKey key) {
        if (key == null) {
            return;
        }
        removeKey(key);
        SfxAnchorRecord anchor = blockData.findAnchorFast(key).orElse(null);
        if (anchor != null) {
            addAnchor(anchor);
        }
    }

    private void addAnchor(SfxAnchorRecord anchor) {
        if (anchor == null || anchor.integrityState() != cc.theends6.sfx.internal.block.SfxBlockIntegrityState.VALID) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null || addons.blockType(instance.typeId()).isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(anchor.key().worldId());
        if (world == null || !world.isChunkLoaded(anchor.key().x() >> 4, anchor.key().z() >> 4)) {
            return;
        }
        loadedByType.computeIfAbsent(instance.typeId(), ignored -> ConcurrentHashMap.newKeySet()).add(anchor.key());
    }

    private void removeKey(SfxBlockAnchorKey key) {
        if (key == null) {
            return;
        }
        for (Map.Entry<String, Set<SfxBlockAnchorKey>> entry : loadedByType.entrySet()) {
            Set<SfxBlockAnchorKey> keys = entry.getValue();
            keys.remove(key);
            if (keys.isEmpty()) {
                loadedByType.remove(entry.getKey(), keys);
            }
        }
    }

    private void removeChunk(UUID worldId, int chunkX, int chunkZ) {
        for (Map.Entry<String, Set<SfxBlockAnchorKey>> entry : loadedByType.entrySet()) {
            Set<SfxBlockAnchorKey> keys = entry.getValue();
            keys.removeIf(key -> worldId.equals(key.worldId())
                    && (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ);
            if (keys.isEmpty()) {
                loadedByType.remove(entry.getKey(), keys);
            }
        }
    }

    private static final class MutableContext implements SfxRandomTickContext<Object> {
        private final UUID instanceId;
        private final String typeId;
        private final Location location;
        private final int speed;
        private Object state;
        private MutableContext(UUID instanceId, String typeId, Location location, Object state, int speed) {
            this.instanceId = instanceId; this.typeId = typeId; this.location = location.clone(); this.state = state; this.speed = speed;
        }
        @Override public UUID instanceId() { return instanceId; }
        @Override public String blockTypeId() { return typeId; }
        @Override public Location location() { return location.clone(); }
        @Override public Player actor() { return null; }
        @Override public Object state() { return state; }
        @Override public void state(Object state) { this.state = state; }
        @Override public World world() { return location.getWorld(); }
        @Override public int randomTickSpeed() { return speed; }
    }
}
