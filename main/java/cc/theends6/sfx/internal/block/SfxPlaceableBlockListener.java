package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.player.PlayerPickBlockEvent;

public final class SfxPlaceableBlockListener implements Listener {
    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final SfxBasicMachineBlockListener basicMachines;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyService energyService;

    public SfxPlaceableBlockListener(
            SfxItems items,
            SfxBlockDataService blockData,
            SfxBasicMachineBlockListener basicMachines,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines,
            SfxEnergyService energyService
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.basicMachines = Objects.requireNonNull(basicMachines, "basicMachines");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.energyService = Objects.requireNonNull(energyService, "energyService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!isPlaceableMarker(marker.itemId(), marker.flags())) {
                return;
            }
            if (blockData.findAnchor(event.getBlockPlaced().getLocation()).isPresent()) {
                return;
            }
            blockData.registerSingleBlock(
                    marker.itemId(),
                    event.getBlockPlaced().getLocation(),
                    event.getBlockPlaced().getType(),
                    event.getPlayer().getUniqueId());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        SfxAnchorRecord anchor = blockData.findAnchor(event.getBlock().getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            blockData.unregisterAt(event.getBlock().getLocation());
            return;
        }
        String typeId = instance.typeId();
        event.setDropItems(false);
        destroyAnchoredBlock(event.getBlock(), instance.instanceId(), typeId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        destroyExplodedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        destroyExplodedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesAnchoredBlock(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesAnchoredBlock(event.getBlocks(), event.getDirection().getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        SfxAnchorRecord anchor = blockData.findAnchor(event.getBlock().getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().getInventory().setItem(event.getTargetSlot(), items.create(instance.typeId()));
        event.getPlayer().updateInventory();
    }

    private void destroyExplodedBlocks(java.util.List<Block> blocks) {
        java.util.List<Block> targets = new java.util.ArrayList<>(blocks);
        for (Block block : targets) {
            SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
            if (anchor == null) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            blocks.remove(block);
            if (instance == null) {
                blockData.unregisterAt(block.getLocation());
                block.setType(org.bukkit.Material.AIR, false);
                continue;
            }
            destroyAnchoredBlock(block, instance.instanceId(), instance.typeId());
            block.setType(org.bukkit.Material.AIR, false);
        }
    }

    private boolean touchesAnchoredBlock(java.util.List<Block> movedBlocks, BlockFace moveDirection) {
        for (Block block : movedBlocks) {
            if (blockData.findAnchor(block.getLocation()).isPresent()) {
                return true;
            }
            if (blockData.findAnchor(block.getRelative(moveDirection).getLocation()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void destroyAnchoredBlock(Block block, java.util.UUID instanceId, String typeId) {
        if (basicMachines.supportsType(typeId)) {
            basicMachines.destroyAnchoredBlock(block, typeId);
            return;
        }
        if (electricMachines.supportsType(typeId)) {
            electricMachines.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (configurableMachines.supportsType(typeId)) {
            configurableMachines.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (energyService.supportsType(typeId)) {
            energyService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        dropStoredContents(block);
        dropPluginBlock(block, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    private boolean isPlaceableMarker(String itemId, java.util.List<String> flags) {
        if (flags.contains("placeable-block")) {
            return true;
        }
        return basicMachines.supportsType(itemId)
                || electricMachines.supportsType(itemId)
                || configurableMachines.supportsType(itemId)
                || energyService.supportsType(itemId);
    }

    private void dropPluginBlock(Block block, String typeId) {
        Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), items.create(typeId));
        dropped.setPickupDelay(0);
    }

    private void dropStoredContents(Block block) {
        if (!(block.getState() instanceof InventoryHolder holder)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (ItemStack content : inventory.getContents()) {
            if (content == null || content.getType().isAir()) {
                continue;
            }
            Item dropped = block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), content.clone());
            dropped.setPickupDelay(0);
        }
        inventory.clear();
    }
}
