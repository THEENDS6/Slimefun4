package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import io.papermc.paper.event.player.PlayerPickBlockEvent;

public final class SfxPlaceableBlockListener implements Listener {
    private static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Set<String> GENERIC_ANCHORED_BLOCKS = Set.of(
            "sf:hardened_glass",
            "sf:wither_proof_obsidian",
            "sf:wither_proof_glass"
    );
    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final SfxBasicMachineBlockListener basicMachines;
    private final SfxElectricMachineService electricMachines;
    private final SfxConfigurableMachineService configurableMachines;
    private final SfxEnergyService energyService;
    private final SfxCargoService cargoService;
    private final SfxDecorationService decorationService;
    private final SfxGpsService gpsService;
    private final SfxAncientAltarService ancientAltarService;
    private final SfxAndroidService androidService;
    private final SfxSpawnerService spawnerService;
    private final SfxBlockPlacerService blockPlacerService;
    private final SfxInfusedHopperService infusedHopperService;
    private final SfxHologramProjectorService hologramProjectorService;
    private final SfxRuntime runtime;

    public SfxPlaceableBlockListener(
            SfxItems items,
            SfxBlockDataService blockData,
            SfxBasicMachineBlockListener basicMachines,
            SfxElectricMachineService electricMachines,
            SfxConfigurableMachineService configurableMachines,
            SfxEnergyService energyService,
            SfxCargoService cargoService,
            SfxDecorationService decorationService,
            SfxGpsService gpsService,
            SfxAncientAltarService ancientAltarService,
            SfxAndroidService androidService,
            SfxSpawnerService spawnerService,
            SfxBlockPlacerService blockPlacerService,
            SfxInfusedHopperService infusedHopperService,
            SfxHologramProjectorService hologramProjectorService,
            SfxRuntime runtime
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.basicMachines = Objects.requireNonNull(basicMachines, "basicMachines");
        this.electricMachines = Objects.requireNonNull(electricMachines, "electricMachines");
        this.configurableMachines = Objects.requireNonNull(configurableMachines, "configurableMachines");
        this.energyService = Objects.requireNonNull(energyService, "energyService");
        this.cargoService = Objects.requireNonNull(cargoService, "cargoService");
        this.decorationService = Objects.requireNonNull(decorationService, "decorationService");
        this.gpsService = Objects.requireNonNull(gpsService, "gpsService");
        this.ancientAltarService = Objects.requireNonNull(ancientAltarService, "ancientAltarService");
        this.androidService = Objects.requireNonNull(androidService, "androidService");
        this.spawnerService = Objects.requireNonNull(spawnerService, "spawnerService");
        this.blockPlacerService = Objects.requireNonNull(blockPlacerService, "blockPlacerService");
        this.infusedHopperService = Objects.requireNonNull(infusedHopperService, "infusedHopperService");
        this.hologramProjectorService = Objects.requireNonNull(hologramProjectorService, "hologramProjectorService");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            if (!isPlaceableMarker(marker.itemId(), marker.flags())) {
                if (marker.flags().contains("placeable-block")) {
                    event.setCancelled(true);
                }
                return;
            }
            if (cargoService.supportsType(marker.itemId()) && !cargoService.canPlace(marker.itemId(), event)) {
                event.setCancelled(true);
                return;
            }
            if (blockData.findAnchor(event.getBlockPlaced().getLocation()).isPresent()) {
                return;
            }
            try {
                java.util.UUID instanceId = blockData.registerSingleBlock(
                        marker.itemId(),
                        event.getBlockPlaced().getLocation(),
                        event.getBlockPlaced().getType(),
                        event.getPlayer().getUniqueId());
                if (decorationService.supportsType(marker.itemId())) {
                    decorationService.handlePlaced(instanceId, marker.itemId());
                }
                if (gpsService.supportsType(marker.itemId())) {
                    gpsService.handlePlaced(instanceId, marker.itemId());
                }
                if (ancientAltarService.supportsType(marker.itemId())) {
                    ancientAltarService.handlePlaced(instanceId, marker.itemId());
                }
                if (androidService.supportsType(marker.itemId())) {
                    androidService.handlePlaced(instanceId, marker.itemId(), event.getPlayer(), event.getBlockPlaced());
                }
                if (spawnerService.supportsType(marker.itemId())) {
                    spawnerService.handlePlaced(instanceId, marker.itemId(), event.getItemInHand());
                }
                if (blockPlacerService.supportsType(marker.itemId())) {
                    blockPlacerService.handlePlaced(instanceId, marker.itemId());
                }
                if (infusedHopperService.supportsType(marker.itemId())) {
                    infusedHopperService.handlePlaced(instanceId, marker.itemId());
                }
                if (hologramProjectorService.supportsType(marker.itemId())) {
                    hologramProjectorService.handlePlaced(instanceId, marker.itemId());
                }
            } catch (RuntimeException exception) {
                event.setCancelled(true);
            }
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
        if (androidService.supportsType(typeId)) {
            androidService.destroyAnchoredBlock(event.getBlock(), instance.instanceId(), typeId);
            return;
        }
        if (spawnerService.supportsType(typeId)) {
            boolean containment = items.readMarker(event.getPlayer().getInventory().getItemInMainHand())
                    .map(marker -> "sf:pickaxe_of_containment".equals(marker.itemId()))
                    .orElse(false);
            spawnerService.destroyAnchoredBlock(event.getBlock(), instance.instanceId(), typeId, containment);
            return;
        }
        destroyAnchoredBlock(event.getBlock(), instance.instanceId(), typeId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        destroyExplodedBlocks(event.blockList(), event.getLocation(), isWitherExplosion(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        destroyExplodedBlocks(event.blockList(), event.getBlock().getLocation().add(0.5, 0.5, 0.5), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
        if (anchor == null) {
            return;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
        event.setCancelled(true);
        if (instance == null) {
            blockData.unregisterAt(block.getLocation());
            block.setType(Material.AIR, false);
            return;
        }
        if (!isWitherExplosion(event.getEntity())) {
            return;
        }
        if (isWitherProof(instance.typeId())) {
            return;
        }
        destroyAnchoredBlock(block, instance.instanceId(), instance.typeId());
        block.setType(Material.AIR, false);
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
    public void onFluidFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (blockData.findAnchor(to.getLocation()).isPresent()) {
            event.setCancelled(true);
            scheduleWaterSourceCheckAbove(to);
            return;
        }
        if (blockData.findAnchor(to.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(to);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        
        
        
        Block block = event.getBlock();
        if (blockData.findAnchor(block.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(block);
        } else if (blockData.findAnchor(block.getLocation()).isPresent()) {
            scheduleWaterSourceCheckAbove(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (blockData.findAnchor(target.getLocation()).isPresent()) {
            event.setCancelled(true);
            scheduleWaterSourceCheckAbove(target);
            return;
        }
        if (blockData.findAnchor(target.getRelative(BlockFace.DOWN).getLocation()).isPresent()) {
            scheduleWaterSourceCheck(target);
        }
    }


    private void scheduleWaterSourceCheckAbove(Block anchoredBlock) {
        scheduleWaterSourceCheck(anchoredBlock.getRelative(BlockFace.UP));
    }

    private void scheduleWaterSourceCheck(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        Location location = block.getLocation();
        runtime.executeAtLater(location, 1L, () -> normalizeWaterSourceAboveAnchoredBlock(block));
    }

    private void normalizeWaterSourceAboveAnchoredBlock(Block block) {
        if (block == null || block.getType() != Material.WATER) {
            return;
        }
        if (blockData.findAnchor(block.getRelative(BlockFace.DOWN).getLocation()).isEmpty()) {
            return;
        }
        if (horizontalWaterSourceCount(block) < 2) {
            return;
        }
        if (block.getBlockData() instanceof Levelled levelled && levelled.getLevel() != 0) {
            levelled.setLevel(0);
            block.setBlockData(levelled, false);
        }
    }

    private int horizontalWaterSourceCount(Block block) {
        int count = 0;
        for (BlockFace face : HORIZONTAL_FACES) {
            Block relative = block.getRelative(face);
            if (relative.getType() == Material.WATER
                    && relative.getBlockData() instanceof Levelled levelled
                    && levelled.getLevel() == 0) {
                count++;
            }
        }
        return count;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        event.getBlocks().removeIf(state -> blockData.findAnchor(state.getLocation()).isPresent());
    }

    private void cancelIfAnchored(org.bukkit.event.Cancellable event, Block block) {
        if (block != null && blockData.findAnchor(block.getLocation()).isPresent()) {
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
        SfxPickBlockSupport.selectOrCreate(event.getPlayer(), items, instance.typeId(), event.getTargetSlot());
    }

    private void destroyExplodedBlocks(java.util.List<Block> blocks, Location explosionCenter, boolean witherExplosion) {
        java.util.List<Block> targets = new java.util.ArrayList<>(blocks);
        for (Block block : targets) {
            SfxAnchorRecord anchor = blockData.findAnchor(block.getLocation()).orElse(null);
            SfxBlockInstanceRecord instance = anchor == null ? null : blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && isExplosionProtected(instance.typeId(), witherExplosion)) {
                blocks.remove(block);
                continue;
            }
            if (isExplosionRayBlocked(explosionCenter, block, witherExplosion)) {
                blocks.remove(block);
                continue;
            }
            if (anchor == null) {
                continue;
            }
            blocks.remove(block);
            if (instance == null) {
                blockData.unregisterAt(block.getLocation());
                block.setType(Material.AIR, false);
                continue;
            }
            destroyAnchoredBlock(block, instance.instanceId(), instance.typeId());
            block.setType(Material.AIR, false);
        }
    }

    private boolean isExplosionRayBlocked(Location explosionCenter, Block target, boolean witherExplosion) {
        if (explosionCenter == null || explosionCenter.getWorld() == null || target.getWorld() == null
                || !explosionCenter.getWorld().equals(target.getWorld())) {
            return false;
        }
        Location targetCenter = target.getLocation().add(0.5, 0.5, 0.5);
        double dx = targetCenter.getX() - explosionCenter.getX();
        double dy = targetCenter.getY() - explosionCenter.getY();
        double dz = targetCenter.getZ() - explosionCenter.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 0.0D) {
            return false;
        }
        
        
        
        
        double step = 0.2D;
        int steps = Math.max(1, (int) Math.ceil(distance / step));
        int targetX = target.getX();
        int targetY = target.getY();
        int targetZ = target.getZ();
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int x = floorBlockCoordinate(explosionCenter.getX() + dx * t);
            int y = floorBlockCoordinate(explosionCenter.getY() + dy * t);
            int z = floorBlockCoordinate(explosionCenter.getZ() + dz * t);
            if (x == targetX && y == targetY && z == targetZ) {
                break;
            }
            if (x == lastX && y == lastY && z == lastZ) {
                continue;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            Block rayBlock = explosionCenter.getWorld().getBlockAt(x, y, z);
            SfxAnchorRecord anchor = blockData.findAnchor(rayBlock.getLocation()).orElse(null);
            if (anchor == null) {
                continue;
            }
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance != null && blocksExplosionRay(instance.typeId(), witherExplosion)) {
                return true;
            }
        }
        return false;
    }

    private int floorBlockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private boolean isExplosionProtected(String typeId, boolean witherExplosion) {
        if (typeId == null) {
            return false;
        }
        if (isWitherProof(typeId)) {
            return true;
        }
        return !witherExplosion && typeId.equals("sf:hardened_glass");
    }

    private boolean blocksExplosionRay(String typeId, boolean witherExplosion) {
        if (typeId == null) {
            return false;
        }
        if (isWitherProof(typeId)) {
            return true;
        }
        return !witherExplosion && typeId.equals("sf:hardened_glass");
    }

    private boolean isWitherProof(String typeId) {
        return typeId.equals("sf:wither_proof_obsidian")
                || typeId.equals("sf:wither_proof_glass")
                || typeId.equals("sf:wither_assembler");
    }

    private boolean isWitherExplosion(Entity entity) {
        return entity instanceof Wither || entity instanceof WitherSkull;
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
        if (cargoService.supportsType(typeId)) {
            cargoService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (gpsService.supportsType(typeId)) {
            gpsService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (decorationService.supportsType(typeId)) {
            decorationService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (ancientAltarService.supportsType(typeId)) {
            ancientAltarService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (androidService.supportsType(typeId)) {
            androidService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (spawnerService.supportsType(typeId)) {
            spawnerService.destroyAnchoredBlock(block, instanceId, typeId, false);
            return;
        }
        if (blockPlacerService.supportsType(typeId)) {
            blockPlacerService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (infusedHopperService.supportsType(typeId)) {
            infusedHopperService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        if (hologramProjectorService.supportsType(typeId)) {
            hologramProjectorService.destroyAnchoredBlock(block, instanceId, typeId);
            return;
        }
        dropStoredContents(block);
        dropPluginBlock(block, typeId);
        blockData.unregisterAt(block.getLocation());
    }

    private boolean isPlaceableMarker(String itemId, java.util.List<String> flags) {
        if (basicMachines.supportsType(itemId)
                || electricMachines.supportsType(itemId)
                || configurableMachines.supportsType(itemId)
                || energyService.supportsType(itemId)
                || cargoService.supportsType(itemId)
                || gpsService.supportsType(itemId)
                || decorationService.supportsType(itemId)
                || ancientAltarService.supportsType(itemId)
                || androidService.supportsType(itemId)
                || spawnerService.supportsType(itemId)
                || blockPlacerService.supportsType(itemId)
                || infusedHopperService.supportsType(itemId)
                || hologramProjectorService.supportsType(itemId)) {
            return true;
        }
        
        
        
        return flags.contains("placeable-block") && GENERIC_ANCHORED_BLOCKS.contains(itemId);
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
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
            SfxBlockDrops.dropItem(block, content.clone());
        }
        inventory.clear();
    }
}
