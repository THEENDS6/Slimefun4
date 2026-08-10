package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.block.SfxBlockEventContext;
import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockLifecycle;
import cc.theends6.sfx.api.block.SfxBlockTransformDecision;
import cc.theends6.sfx.api.block.SfxBlockType;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;


public final class SfxAddonBlockLifecycleService {
    private final SfxBlockDataService blockData;
    private final SfxAddonManager addons;
    private final Logger logger;
    private final Set<UUID> quarantined = ConcurrentHashMap.newKeySet();

    public SfxAddonBlockLifecycleService(SfxBlockDataService blockData, SfxAddonManager addons, Logger logger) {
        this.blockData = blockData;
        this.addons = addons;
        this.logger = logger;
    }

    public boolean initializePlaced(Block block, Player actor) {
        Resolved<?> resolved = resolve(block == null ? null : block.getLocation(), true).orElse(null);
        if (resolved == null) return true;
        if (!resolved.type().anchorMaterials().contains(block.getType())) {
            quarantine(resolved.instance(), new IllegalStateException("Anchor material " + block.getType()
                    + " is not declared by " + resolved.type().id()));
            return false;
        }
        return initialize(resolved, block.getLocation(), actor, true);
    }

    public void onLoad(Chunk chunk) { forChunk(chunk, lifecycle -> lifecycle::onLoad); }
    public void onUnload(Chunk chunk) { forChunk(chunk, lifecycle -> lifecycle::onUnload); }

    public void onWorldUnload(World world) {
        if (world == null) return;
        for (SfxAnchorRecord anchor : blockData.anchorsInWorld(world.getUID())) {
            Location location = new Location(world, anchor.key().x(), anchor.key().y(), anchor.key().z());
            invoke(location, null, lifecycle -> lifecycle::onUnload);
        }
    }

    public void onInteract(Block block, Player actor) {
        invoke(block == null ? null : block.getLocation(), actor, lifecycle -> lifecycle::onInteract);
    }

    public void onPhysics(Block block) {
        invoke(block == null ? null : block.getLocation(), null, lifecycle -> lifecycle::onPhysicsUpdate);
    }

    public void onNeighborUpdate(Block changed) {
        if (changed == null) return;
        for (org.bukkit.block.BlockFace face : List.of(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
            Block neighbor = changed.getRelative(face);
            invoke(neighbor.getLocation(), null, lifecycle -> lifecycle::onNeighborUpdate);
        }
    }

    public void onPistonMove(List<Block> blocks) {
        if (blocks == null) return;
        for (Block block : blocks) invoke(block.getLocation(), null, lifecycle -> lifecycle::onPistonMove);
    }

    public void onFluidContact(Block block) {
        invoke(block == null ? null : block.getLocation(), null, lifecycle -> lifecycle::onFluidBreak);
    }

    public Optional<SfxBlockTransformDecision> onVanillaTransform(Block block, Material to) {
        Resolved<?> resolved = resolve(block == null ? null : block.getLocation(), true).orElse(null);
        if (resolved == null || quarantined.contains(resolved.instance().instanceId())) return Optional.empty();
        try {
            return Optional.of(invokeTransform(resolved, block.getLocation(), block.getType(), to));
        } catch (RuntimeException exception) {
            quarantine(resolved.instance(), exception);
            return Optional.of(SfxBlockTransformDecision.cancel());
        }
    }

    public boolean replaceWithCustom(Block block, Player actor, SfxBlockTransformDecision decision) {
        if (block == null || decision == null || decision.action() != SfxBlockTransformDecision.Action.REPLACE_WITH_CUSTOM_BLOCK) return false;
        SfxBlockType<?> replacement = addons.blockType(decision.replacementTypeId()).orElse(null);
        Material material = decision.replacementMaterial();
        SfxAnchorRecord anchor = blockData.findAnchorFast(block.getLocation()).orElse(null);
        SfxBlockInstanceRecord current = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        if (replacement == null || current == null || material == null || !replacement.anchorMaterials().contains(material)) return false;
        try {
            Object state = replacement.initialState().get();
            MutableContext<Object> context = new MutableContext<>(current.instanceId(), replacement.id(), block.getLocation(), actor, state);
            ((SfxBlockLifecycle) replacement.lifecycle()).onPlace(context);
            byte[] payload = encode(replacement, context.state());
            Material previous = block.getType();
            block.setType(material, false);
            boolean replaced = blockData.replaceInstanceType(current.instanceId(), replacement.id(), material,
                    payload, replacement.stateSchema().version());
            if (!replaced) block.setType(previous, false);
            if (replaced) quarantined.remove(current.instanceId());
            return replaced;
        } catch (RuntimeException exception) {
            quarantine(current, exception);
            return false;
        }
    }

    public void reconcileAllowedTransform(Block block, Material expectedMaterial) {
        if (block == null || block.getType() != expectedMaterial) return;
        SfxAnchorRecord anchor = blockData.findAnchorFast(block.getLocation()).orElse(null);
        SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        SfxBlockType<?> type = instance == null ? null : addons.blockType(instance.typeId()).orElse(null);
        if (type == null) return;
        if (!type.anchorMaterials().contains(expectedMaterial)) {
            blockData.unregisterAt(block.getLocation());
            quarantined.remove(instance.instanceId());
            return;
        }
        blockData.replaceInstanceType(instance.instanceId(), instance.typeId(), expectedMaterial,
                instance.stateBlob(), instance.version());
    }

    public boolean quarantined(UUID instanceId) { return quarantined.contains(instanceId); }

    public Optional<SfxBlockInstanceRecord> managedInstance(Block block) {
        return resolve(block == null ? null : block.getLocation(), true).map(Resolved::instance);
    }

    private void forChunk(Chunk chunk, LifecycleActionFactory factory) {
        if (chunk == null) return;
        World world = chunk.getWorld();
        for (SfxAnchorRecord anchor : blockData.anchorsInChunk(world.getUID(), chunk.getX(), chunk.getZ())) {
            Location location = new Location(world, anchor.key().x(), anchor.key().y(), anchor.key().z());
            invoke(location, null, factory);
        }
    }

    private void invoke(Location location, Player actor, LifecycleActionFactory factory) {
        Resolved<?> resolved = resolve(location, false).orElse(null);
        if (resolved == null || quarantined.contains(resolved.instance().instanceId())) return;
        invokeResolved(resolved, location, actor, factory);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void invokeResolved(Resolved resolved, Location location, Player actor, LifecycleActionFactory factory) {
        try {
            Object state = decodeOrInitial(resolved.type(), resolved.instance());
            MutableContext context = new MutableContext(resolved.instance().instanceId(), resolved.type().id(), location, actor, state);
            factory.create(resolved.type().lifecycle()).accept(context);
            persist(resolved.type(), resolved.instance(), context.state());
        } catch (RuntimeException exception) {
            quarantine(resolved.instance(), exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SfxBlockTransformDecision invokeTransform(Resolved resolved, Location location, Material from, Material to) {
        Object state = decodeOrInitial(resolved.type(), resolved.instance());
        MutableContext context = new MutableContext(resolved.instance().instanceId(), resolved.type().id(), location, null, state);
        SfxBlockTransformDecision decision = resolved.type().lifecycle().onVanillaTransform(context, from, to);
        persist(resolved.type(), resolved.instance(), context.state());
        return decision == null ? SfxBlockTransformDecision.cancel() : decision;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean initialize(Resolved resolved, Location location, Player actor, boolean placement) {
        try {
            Object state = resolved.type().initialState().get();
            MutableContext context = new MutableContext(resolved.instance().instanceId(), resolved.type().id(), location, actor, state);
            if (placement) resolved.type().lifecycle().onPlace(context);
            persist(resolved.type(), resolved.instance(), context.state());
            quarantined.remove(resolved.instance().instanceId());
            return true;
        } catch (RuntimeException exception) {
            quarantine(resolved.instance(), exception);
            return false;
        }
    }

    private Optional<Resolved<?>> resolve(Location location, boolean validateWorldAnchor) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        SfxAnchorRecord anchor = (validateWorldAnchor ? blockData.findAnchorAndValidate(location) : blockData.findAnchorFast(location)).orElse(null);
        SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance != null && instance.lifecycleState() == cc.theends6.sfx.api.block.SfxBlockLifecycleState.INVALID) {
            quarantined.add(instance.instanceId());
            return Optional.empty();
        }
        SfxBlockType<?> type = instance == null ? null : addons.blockType(instance.typeId()).orElse(null);
        return type == null ? Optional.empty() : Optional.of(new Resolved<>(instance, type));
    }

    private Object decodeOrInitial(SfxBlockType<?> type, SfxBlockInstanceRecord instance) {
        return !instance.hasState() ? type.initialState().get()
                : type.stateSchema().decode(instance.version(), instance.stateBlob());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void persist(SfxBlockType type, SfxBlockInstanceRecord instance, Object state) {
        blockData.updateInstanceState(instance.instanceId(), type.stateSchema().codec().encode(state),
                instance.lifecycleState(), type.stateSchema().version());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static byte[] encode(SfxBlockType type, Object state) { return type.stateSchema().codec().encode(state); }
    private void quarantine(SfxBlockInstanceRecord instance, RuntimeException exception) {
        quarantined.add(instance.instanceId());
        blockData.updateInstanceState(instance.instanceId(), instance.stateBlob(),
                cc.theends6.sfx.api.block.SfxBlockLifecycleState.INVALID, instance.version());
        logger.log(Level.SEVERE, "Quarantined addon block state " + instance.typeId() + " at "
                + instance.anchorKey() + " after state/lifecycle failure", exception);
    }

    private record Resolved<S>(SfxBlockInstanceRecord instance, SfxBlockType<S> type) {}

    @FunctionalInterface private interface LifecycleActionFactory {
        LifecycleAction create(SfxBlockLifecycle lifecycle);
    }
    @FunctionalInterface private interface LifecycleAction {
        void accept(SfxBlockEventContext context);
    }

    private static final class MutableContext<S> implements SfxBlockEventContext<S> {
        private final UUID instanceId;
        private final String typeId;
        private final Location location;
        private final Player actor;
        private S state;
        private MutableContext(UUID instanceId, String typeId, Location location, Player actor, S state) {
            this.instanceId = instanceId; this.typeId = typeId; this.location = location.clone(); this.actor = actor; this.state = state;
        }
        @Override public UUID instanceId() { return instanceId; }
        @Override public String blockTypeId() { return typeId; }
        @Override public Location location() { return location.clone(); }
        @Override public Player actor() { return actor; }
        @Override public S state() { return state; }
        @Override public void state(S state) { this.state = state; }
    }
}
