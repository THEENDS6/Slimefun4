package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.world.SfxWorldActionResult;
import cc.theends6.sfx.api.world.SfxWorldActionService;
import cc.theends6.sfx.api.world.SfxRangeBlockBreakRequest;
import cc.theends6.sfx.api.world.SfxRangeWorldActionResult;
import cc.theends6.sfx.api.container.SfxTransactionReservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;


public final class DefaultSfxWorldActions implements SfxWorldActionService {
    private static final int MAX_RANGE_BLOCKS = 256;
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final DefaultSfxProtectionService protection;

    public DefaultSfxWorldActions(JavaPlugin plugin, SfxRuntime runtime, DefaultSfxProtectionService protection) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.protection = protection;
    }

    @Override
    public CompletableFuture<SfxWorldActionResult> breakBlock(Player actor, Location location, ItemStack tool, boolean drops) {
        if (actor == null || location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor and world location are required"));
        }
        return runtime.supplyAtAsync(location, () -> {
            Block block = location.getBlock();
            if (Boolean.FALSE.equals(protection.breakAdapterDecision(actor, block))) {
                return SfxWorldActionResult.protectedAction();
            }
            Material before = block.getType();
            BlockBreakEvent event = new BlockBreakEvent(block, actor);
            event.setDropItems(drops);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return SfxWorldActionResult.protectedAction();
            if (block.getType() != before || block.getType().isAir()) return SfxWorldActionResult.succeeded();
            boolean changed = event.isDropItems() ? block.breakNaturally(tool == null ? actor.getInventory().getItemInMainHand() : tool)
                    : setAir(block);
            return changed ? SfxWorldActionResult.succeeded() : SfxWorldActionResult.failed("block mutation was rejected");
        });
    }

    @Override
    public CompletableFuture<SfxWorldActionResult> replaceBlock(Player actor, Location location, Material material,
                                                                 ItemStack placementItem, boolean physics) {
        if (actor == null || location == null || location.getWorld() == null || material == null || !material.isBlock()) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor, block material and world location are required"));
        }
        return runtime.supplyAtAsync(location, () -> {
            Block block = location.getBlock();
            if (Boolean.FALSE.equals(protection.placeAdapterDecision(actor, block, placementItem))) {
                return SfxWorldActionResult.protectedAction();
            }
            ItemStack used = placementItem == null ? new ItemStack(material) : placementItem;
            BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(),
                    block.getRelative(org.bukkit.block.BlockFace.DOWN), used, actor, true, EquipmentSlot.HAND);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || !event.canBuild()) return SfxWorldActionResult.protectedAction();
            block.setType(material, physics);
            return SfxWorldActionResult.succeeded();
        });
    }

    @Override public CompletableFuture<SfxWorldActionResult> damageEntity(Player actor, LivingEntity target,
                                                                           double damage) {
        if (actor == null || target == null || !Double.isFinite(damage) || damage <= 0.0D) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor, target and positive damage are required"));
        }
        return runtime.supplyAtAsync(target.getLocation(), () -> {
            if (!target.isValid() || !protection.canDamage(actor, target)) return SfxWorldActionResult.protectedAction();
            target.damage(damage, actor);
            return SfxWorldActionResult.succeeded();
        });
    }

    @Override public CompletableFuture<SfxWorldActionResult> spawnEntity(Player actor, Location location,
                                                                          EntityType type) {
        if (actor == null || location == null || location.getWorld() == null || type == null || !type.isSpawnable()) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor, world location and spawnable entity type are required"));
        }
        return runtime.supplyAtAsync(location, () -> {
            if (!protection.canUseItem(actor, location, null)) return SfxWorldActionResult.protectedAction();
            location.getWorld().spawnEntity(location, type);
            return SfxWorldActionResult.succeeded();
        });
    }

    @Override public CompletableFuture<SfxWorldActionResult> applyEffect(Player actor, LivingEntity target,
                                                                          PotionEffect effect) {
        if (actor == null || target == null || effect == null) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor, target and effect are required"));
        }
        return runtime.supplyAtAsync(target.getLocation(), () -> {
            if (!target.isValid() || !protection.canDamage(actor, target)) return SfxWorldActionResult.protectedAction();
            target.addPotionEffect(effect);
            return SfxWorldActionResult.succeeded();
        });
    }

    @Override public CompletableFuture<SfxRangeWorldActionResult> breakBlocks(SfxRangeBlockBreakRequest request) {
        if (request == null) return CompletableFuture.completedFuture(range(
                SfxRangeWorldActionResult.Status.INVALID, 0, 0, "request is required"));
        List<Location> raw = request.locations();
        if (raw.isEmpty() || raw.size() > MAX_RANGE_BLOCKS || raw.stream().anyMatch(location ->
                location == null || location.getWorld() == null)) {
            return CompletableFuture.completedFuture(range(SfxRangeWorldActionResult.Status.INVALID,
                    raw.size(), 0, "between 1 and " + MAX_RANGE_BLOCKS + " world locations are required"));
        }
        Map<String, Location> unique = new LinkedHashMap<>();
        UUID worldId = raw.get(0).getWorld().getUID();
        for (Location location : raw) {
            if (!worldId.equals(location.getWorld().getUID())) return CompletableFuture.completedFuture(range(
                    SfxRangeWorldActionResult.Status.CROSS_REGION, raw.size(), 0,
                    "range actions cannot span worlds"));
            String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
            unique.putIfAbsent(key, location);
        }
        List<Location> locations = List.copyOf(unique.values());
        return runtime.supplyAtAsync(locations.get(0), () -> executeRangeBreak(request, locations));
    }

    private SfxRangeWorldActionResult executeRangeBreak(SfxRangeBlockBreakRequest request,
                                                         List<Location> locations) {
        if (locations.stream().anyMatch(location -> !plugin.getServer().isOwnedByCurrentRegion(location))) {
            return range(SfxRangeWorldActionResult.Status.CROSS_REGION, locations.size(), 0,
                    "targets span scheduler regions; split the request by region");
        }
        List<PreparedBreak> prepared = new ArrayList<>();
        for (Location location : locations) {
            Block block = location.getBlock();
            if (block.getType().isAir()) continue;
            if (Boolean.FALSE.equals(protection.breakAdapterDecision(request.actor(), block))) {
                return range(SfxRangeWorldActionResult.Status.PROTECTED, locations.size(), 0,
                        "protection adapter rejected a target");
            }
            Material before = block.getType();
            BlockBreakEvent event = new BlockBreakEvent(block, request.actor());
            event.setDropItems(request.drops());
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || block.getType() != before) return range(
                    SfxRangeWorldActionResult.Status.PROTECTED, locations.size(), 0,
                    "a block break event rejected or changed a target");
            prepared.add(new PreparedBreak(block, event.isDropItems()));
        }
        if (prepared.isEmpty()) return range(SfxRangeWorldActionResult.Status.SUCCESS,
                locations.size(), 0, null);
        Optional<SfxTransactionReservation> resource;
        try { resource = request.resourceReservation().apply(prepared.size()); }
        catch (RuntimeException exception) {
            return range(SfxRangeWorldActionResult.Status.RESOURCE_REJECTED, locations.size(), 0,
                    exception.getMessage());
        }
        if (resource.isEmpty()) return range(SfxRangeWorldActionResult.Status.RESOURCE_REJECTED,
                locations.size(), 0, "resource reservation was rejected");
        try { resource.get().commit(); }
        catch (RuntimeException exception) {
            safeRollback(resource.get());
            return range(SfxRangeWorldActionResult.Status.RESOURCE_REJECTED, locations.size(), 0,
                    exception.getMessage());
        }
        int changed = 0;
        ItemStack tool = request.tool() == null ? request.actor().getInventory().getItemInMainHand() : request.tool();
        for (PreparedBreak target : prepared) {
            boolean success = target.drops() ? target.block().breakNaturally(tool) : setAir(target.block());
            if (!success) break;
            changed++;
        }
        if (changed == prepared.size()) return range(SfxRangeWorldActionResult.Status.SUCCESS,
                locations.size(), changed, null);
        return range(changed == 0 ? SfxRangeWorldActionResult.Status.FAILED : SfxRangeWorldActionResult.Status.PARTIAL,
                locations.size(), changed, "world mutation stopped after an unexpected block failure");
    }

    private static void safeRollback(SfxTransactionReservation reservation) {
        try { reservation.rollback(); } catch (RuntimeException ignored) { }
    }

    private static SfxRangeWorldActionResult range(SfxRangeWorldActionResult.Status status,
                                                    int requested, int succeeded, String message) {
        return new SfxRangeWorldActionResult(status, requested, succeeded, message);
    }

    private record PreparedBreak(Block block, boolean drops) { }

    private static boolean setAir(Block block) {
        if (block.getType().isAir()) return false;
        block.setType(Material.AIR, false);
        return true;
    }
}
