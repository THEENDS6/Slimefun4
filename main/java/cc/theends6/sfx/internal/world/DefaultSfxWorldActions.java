package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.permission.SfxActionActor;
import cc.theends6.sfx.api.permission.SfxWorldPermissionService;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;









public final class DefaultSfxWorldActions implements SfxWorldActionService {
    private static final int MAX_RANGE_BLOCKS = 256;
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxWorldPermissionService permissions;

    public DefaultSfxWorldActions(JavaPlugin plugin, SfxRuntime runtime, SfxWorldPermissionService permissions) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.permissions = permissions;
    }

    @Override
    public CompletableFuture<SfxWorldActionResult> breakBlock(Player actor, Location location, ItemStack tool, boolean drops) {
        if (actor == null || location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor and world location are required"));
        }
        return runtime.supplyAtAsync(location, () -> {
            Block block = location.getBlock();
            if (!permissions.canBreak(SfxActionActor.player(actor), block)) {
                return SfxWorldActionResult.protectedAction();
            }
            if (block.getType().isAir()) {
                return SfxWorldActionResult.succeeded();
            }
            boolean changed = drops
                    ? block.breakNaturally(tool == null ? actor.getInventory().getItemInMainHand() : tool)
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
            ItemStack used = placementItem == null ? new ItemStack(material) : placementItem;
            if (!permissions.canPlace(SfxActionActor.player(actor), block, used)) {
                return SfxWorldActionResult.protectedAction();
            }
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
            if (!target.isValid() || !permissions.canDamage(SfxActionActor.player(actor), target)) {
                return SfxWorldActionResult.protectedAction();
            }
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
            if (!permissions.canSpawn(SfxActionActor.player(actor), location, type)) {
                return SfxWorldActionResult.protectedAction();
            }
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
            if (!target.isValid() || !permissions.canDamage(SfxActionActor.player(actor), target)) {
                return SfxWorldActionResult.protectedAction();
            }
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
        SfxActionActor actor = SfxActionActor.player(request.actor());
        List<PreparedBreak> prepared = new ArrayList<>();
        for (Location location : locations) {
            Block block = location.getBlock();
            if (block.getType().isAir()) continue;
            if (!permissions.canBreak(actor, block)) {
                return range(SfxRangeWorldActionResult.Status.PROTECTED, locations.size(), 0,
                        "a target is protected");
            }
            prepared.add(new PreparedBreak(block, request.drops()));
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
