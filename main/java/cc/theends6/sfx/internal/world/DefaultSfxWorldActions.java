package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.world.SfxWorldActionResult;
import cc.theends6.sfx.api.world.SfxWorldActionService;
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

    private static boolean setAir(Block block) {
        if (block.getType().isAir()) return false;
        block.setType(Material.AIR, false);
        return true;
    }
}
