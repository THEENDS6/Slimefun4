package cc.theends6.sfx.internal.world;

import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.world.SfxProtectionService;
import cc.theends6.sfx.api.world.SfxWorldActionResult;
import cc.theends6.sfx.api.world.SfxWorldActionService;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;


public final class DefaultSfxWorldActions implements SfxWorldActionService, SfxProtectionService {
    private final JavaPlugin plugin;
    private final SfxRuntime runtime;

    public DefaultSfxWorldActions(JavaPlugin plugin, SfxRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public CompletableFuture<SfxWorldActionResult> breakBlock(Player actor, Location location, ItemStack tool, boolean drops) {
        if (actor == null || location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(SfxWorldActionResult.invalid("actor and world location are required"));
        }
        return runtime.supplyAtAsync(location, () -> {
            Block block = location.getBlock();
            if (!canBreak(actor, block)) return SfxWorldActionResult.protectedAction();
            boolean changed = drops ? block.breakNaturally(tool == null ? actor.getInventory().getItemInMainHand() : tool)
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
            if (!canPlace(actor, block, placementItem)) return SfxWorldActionResult.protectedAction();
            block.setType(material, physics);
            return SfxWorldActionResult.succeeded();
        });
    }

    @Override public boolean canBreak(Player player, Block block) {
        if (!sameWorld(player, block)) return false;
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override public boolean canPlace(Player player, Block block, ItemStack item) {
        if (!sameWorld(player, block)) return false;
        BlockState replaced = block.getState();
        Block against = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, against,
                item == null ? new ItemStack(block.getType()) : item, player, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild();
    }

    @Override public boolean canInteract(Player player, Block block) { return sameWorld(player, block); }
    @Override public boolean canDamage(Player player, Entity entity) {
        return player != null && entity != null && player.getWorld().equals(entity.getWorld());
    }
    @Override public boolean canUseItem(Player player, Location location, ItemStack item) {
        return player != null && location != null && location.getWorld() != null && player.getWorld().equals(location.getWorld());
    }

    private static boolean sameWorld(Player player, Block block) {
        return player != null && block != null && player.getWorld().equals(block.getWorld());
    }

    private static boolean setAir(Block block) {
        if (block.getType().isAir()) return false;
        block.setType(Material.AIR, false);
        return true;
    }
}
