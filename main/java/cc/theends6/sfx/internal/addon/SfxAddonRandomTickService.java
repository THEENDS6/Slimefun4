package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockEventContext;
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
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;


public final class SfxAddonRandomTickService implements Listener {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxBlockDataService blockData;
    private final SfxAddonManager addons;
    private final Map<String, List<SfxAnchorRecord>> loadedByType = new ConcurrentHashMap<>();
    private final Set<cc.theends6.sfx.api.block.SfxBlockAnchorKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> quarantined = ConcurrentHashMap.newKeySet();
    private volatile long indexedRevision = -1L;
    private volatile boolean running;

    public SfxAddonRandomTickService(JavaPlugin plugin, SfxRuntime runtime,
                                     SfxBlockDataService blockData, SfxAddonManager addons) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.blockData = blockData;
        this.addons = addons;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        rebuildLoadedIndex();
        scheduleNext();
    }

    public synchronized void shutdown() {
        running = false;
        loadedByType.clear();
        inFlight.clear();
        quarantined.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) { indexedRevision = -1L; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) { indexedRevision = -1L; }

    private void scheduleNext() {
        if (!running) return;
        runtime.executeGlobalLater(1L, () -> {
            if (!running) return;
            tick();
            scheduleNext();
        });
    }

    private void tick() {
        if (indexedRevision != blockData.revision()) rebuildLoadedIndex();
        Collection<SfxRandomTickType<?>> definitions = addons.randomTickTypes();
        for (SfxRandomTickType<?> definition : definitions) {
            List<SfxAnchorRecord> candidates = loadedByType.getOrDefault(definition.blockTypeId(), List.of());
            if (candidates.isEmpty()) continue;
            Map<World, List<SfxAnchorRecord>> byWorld = new LinkedHashMap<>();
            for (SfxAnchorRecord candidate : candidates) {
                World world = Bukkit.getWorld(candidate.key().worldId());
                if (world != null && world.isChunkLoaded(candidate.key().x() >> 4, candidate.key().z() >> 4)) {
                    byWorld.computeIfAbsent(world, ignored -> new ArrayList<>()).add(candidate);
                }
            }
            for (Map.Entry<World, List<SfxAnchorRecord>> entry : byWorld.entrySet()) {
                int speed = randomTickSpeed(entry.getKey());
                if (definition.affectedByGameRule() && speed <= 0) continue;
                int attempts = Math.min(definition.perTickBudget(), Math.max(1,
                        (int) Math.round((definition.affectedByGameRule() ? speed : 1) * definition.weight())));
                for (int attempt = 0; attempt < attempts; attempt++) {
                    List<SfxAnchorRecord> worldCandidates = entry.getValue();
                    SfxAnchorRecord selected = worldCandidates.get(ThreadLocalRandom.current().nextInt(worldCandidates.size()));
                    dispatch(definition, selected, entry.getKey(), speed);
                }
            }
        }
    }

    private void dispatch(SfxRandomTickType<?> definition, SfxAnchorRecord anchor, World world, int speed) {
        if (!inFlight.add(anchor.key())) return;
        Location location = new Location(world, anchor.key().x(), anchor.key().y(), anchor.key().z());
        runtime.executeAt(location, () -> {
            try {
                SfxAnchorRecord currentAnchor = blockData.findAnchorAndValidate(location).orElse(null);
                if (currentAnchor == null || !currentAnchor.instanceId().equals(anchor.instanceId())) return;
                SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
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
                inFlight.remove(anchor.key());
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void invoke(SfxRandomTickType definition, SfxBlockType blockType,
                        SfxBlockInstanceRecord instance, Location location, int speed) {
        Object state = instance.stateBlob().length == 0 ? blockType.initialState().get()
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
        Map<String, List<SfxAnchorRecord>> rebuilt = new LinkedHashMap<>();
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            World world = Bukkit.getWorld(anchor.key().worldId());
            if (world == null || !world.isChunkLoaded(anchor.key().x() >> 4, anchor.key().z() >> 4)) continue;
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && addons.blockType(instance.typeId()).isPresent()) {
                rebuilt.computeIfAbsent(instance.typeId(), ignored -> new ArrayList<>()).add(anchor);
            }
        }
        loadedByType.clear();
        rebuilt.forEach((type, anchors) -> loadedByType.put(type, List.copyOf(anchors)));
        indexedRevision = blockData.revision();
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
