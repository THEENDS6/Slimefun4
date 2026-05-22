package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SfxInfusedHopperService implements SfxProgrammaticBlockPlacement, Listener {
    public static final String INFUSED_HOPPER = "sf:infused_hopper";

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final Map<SfxBlockAnchorKey, HopperTickState> tickStates = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile long tickClock;

    public SfxInfusedHopperService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxBlockDataService blockData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
    }

    public void start() {
        running = true;
        tickClock = 0L;
        scheduleTick();
    }

    public void shutdown() {
        running = false;
        tickStates.clear();
    }

    public boolean supportsType(String typeId) {
        return INFUSED_HOPPER.equals(typeId);
    }

    public void handlePlaced(UUID instanceId, String typeId) {
        // Stateless runtime. The ticker discovers persisted anchors.
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        tickStates.remove(SfxBlockAnchorKey.fromLocation(block.getLocation()));
        dropStoredContents(block);
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    @Override
    public boolean canPlaceFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        return supportsType(itemId) && target != null && target.getType().isAir();
    }

    @Override
    public boolean placeFromBlockPlacer(String itemId, ItemStack stack, Block target, UUID ownerId) {
        if (!canPlaceFromBlockPlacer(itemId, stack, target, ownerId)) {
            return false;
        }
        target.setType(Material.HOPPER, true);
        blockData.registerSingleBlock(itemId, target.getLocation(), Material.HOPPER, ownerId);
        tickStates.remove(SfxBlockAnchorKey.fromLocation(target.getLocation()));
        return true;
    }

    private void scheduleTick() {
        long baseInterval = baseIntervalTicks();
        runtime.executeGlobalLater(baseInterval, () -> {
            if (!running) {
                return;
            }
            tickClock += baseInterval;
            tickAll(tickClock);
            scheduleTick();
        });
    }

    private void tickAll(long nowTick) {
        List<SfxAnchorRecord> anchors = blockData.anchors();
        for (SfxAnchorRecord anchor : anchors) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !supportsType(instance.typeId())) {
                continue;
            }
            HopperTickState state = tickStates.computeIfAbsent(anchor.key(), ignored -> new HopperTickState());
            if (!state.markScheduledIfDue(nowTick)) {
                continue;
            }
            World world = Bukkit.getWorld(anchor.key().worldId());
            if (world == null) {
                finishTick(anchor.key(), state, nowTick, false, false);
                continue;
            }
            Location location = new Location(world, anchor.key().x(), anchor.key().y(), anchor.key().z());
            runtime.executeAt(location, () -> {
                TickResult result = tickOne(location);
                finishTick(anchor.key(), state, nowTick, result.validBlock(), result.movedItems());
            });
        }
    }

    private void finishTick(SfxBlockAnchorKey key, HopperTickState state, long nowTick, boolean validBlock, boolean movedItems) {
        if (!validBlock) {
            tickStates.remove(key);
            return;
        }
        long baseInterval = baseIntervalTicks();
        long maxInterval = Math.max(baseInterval, baseInterval * Math.max(1L, plugin.getConfig().getLong("legacy.infused-hopper.max-idle-multiplier", 10L)));
        long backoffAfterTicks = Math.max(baseInterval, plugin.getConfig().getLong("legacy.infused-hopper.idle-backoff-after-seconds", 30L) * 20L);
        state.finish(nowTick, movedItems, baseInterval, maxInterval, backoffAfterTicks);
    }

    private TickResult tickOne(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.HOPPER) {
            blockData.unregisterAt(location);
            return new TickResult(false, false);
        }
        if (plugin.getConfig().getBoolean("legacy.infused-hopper.redstone-disables", false)
                && block.getBlockData() instanceof org.bukkit.block.data.type.Hopper hopper && !hopper.isEnabled()) {
            return new TickResult(true, false);
        }
        double radius = Math.max(0.1D, plugin.getConfig().getDouble("legacy.infused-hopper.radius", 3.5D));
        Location pull = block.getLocation().add(0.5D, 1.2D, 0.5D);
        boolean moved = false;
        for (Entity entity : block.getWorld().getNearbyEntities(pull, radius, radius, radius)) {
            if (!isValidItem(pull, entity)) {
                continue;
            }
            Item item = (Item) entity;
            if (teleportItemToHopper(item, pull)) {
                moved = true;
            }
        }
        if (moved) {
            int particleCount = plugin.getConfig().getInt("legacy.infused-hopper.particle-count", 12);
            if (particleCount > 0) {
                block.getWorld().spawnParticle(Particle.PORTAL, pull, particleCount, 0.35D, 0.35D, 0.35D, 0.02D);
            }
            if (!plugin.getConfig().getBoolean("legacy.infused-hopper.silent", false)) {
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 0.5F, 2.0F);
            }
        }
        return new TickResult(true, moved);
    }


    private boolean teleportItemToHopper(Item item, Location pull) {
        Location target = pull.clone();
        boolean teleported = item.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (!teleported || !item.isValid()) {
            return false;
        }
        item.setVelocity(new Vector(0.0D, 0.1D, 0.0D));
        return true;
    }

    private boolean isValidItem(Location pull, Entity entity) {
        if (!(entity instanceof Item item) || !entity.isValid()) {
            return false;
        }
        if (plugin.getConfig().getBoolean("legacy.infused-hopper.respect-pickup-delay", false) && item.getPickupDelay() > 0) {
            return false;
        }
        if (item.getLocation().distanceSquared(pull) <= 0.25D) {
            return false;
        }
        return !item.hasMetadata("no_pickup") && !item.hasMetadata("no-pickup") && !item.hasMetadata("sf_no_pickup") && !item.hasMetadata("slimefun_no_pickup");
    }

    private long baseIntervalTicks() {
        long configured = plugin.getConfig().getLong("legacy.infused-hopper.base-interval-ticks", Long.MIN_VALUE);
        if (configured == Long.MIN_VALUE) {
            configured = plugin.getConfig().getLong("legacy.infused-hopper.interval-ticks", 10L);
        }
        return Math.max(1L, configured);
    }

    private void dropStoredContents(Block block) {
        if (!(block.getState() instanceof org.bukkit.inventory.InventoryHolder holder)) {
            return;
        }
        org.bukkit.inventory.Inventory inventory = holder.getInventory();
        for (ItemStack content : inventory.getContents()) {
            if (content == null || content.getType().isAir()) {
                continue;
            }
            SfxBlockDrops.dropItem(block, content.clone());
        }
        inventory.clear();
    }

    private static final class HopperTickState {
        private long nextRunTick;
        private long currentIntervalTicks;
        private long idleTicks;
        private boolean inFlight;

        private synchronized boolean markScheduledIfDue(long nowTick) {
            if (inFlight || nowTick < nextRunTick) {
                return false;
            }
            inFlight = true;
            return true;
        }

        private synchronized void finish(long nowTick, boolean movedItems, long baseIntervalTicks, long maxIntervalTicks, long backoffAfterTicks) {
            inFlight = false;
            if (currentIntervalTicks <= 0L) {
                currentIntervalTicks = baseIntervalTicks;
            }
            if (movedItems) {
                idleTicks = 0L;
                currentIntervalTicks = baseIntervalTicks;
            } else {
                idleTicks += currentIntervalTicks;
                if (idleTicks >= backoffAfterTicks) {
                    currentIntervalTicks = Math.min(maxIntervalTicks, Math.max(baseIntervalTicks, currentIntervalTicks * 2L));
                } else {
                    currentIntervalTicks = baseIntervalTicks;
                }
            }
            nextRunTick = nowTick + currentIntervalTicks;
        }
    }

    private record TickResult(boolean validBlock, boolean movedItems) {
    }
}
