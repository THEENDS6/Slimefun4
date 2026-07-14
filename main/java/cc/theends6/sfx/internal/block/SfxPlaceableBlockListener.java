package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.player.PlayerPickBlockEvent;

public final class SfxPlaceableBlockListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger("SlimeFunX");

    private final SfxItems items;
    private final SfxBlockDataService blockData;
    private final SfxBlockLifecycleRouter lifecycleRouter;
    private final SfxBlockPlacementRouter placementRouter;
    private final SfxBlockExplosionService explosionService;
    private final SfxAnchoredBlockEnvironmentalGuard environmentalGuard;

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
            SfxRuntime runtime,
            SfxMachineRuntimeEngine machineRuntime
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(machineRuntime, "machineRuntime");
        this.lifecycleRouter = new SfxBlockLifecycleRouter(
                this.items,
                this.blockData,
                Objects.requireNonNull(basicMachines, "basicMachines"),
                Objects.requireNonNull(electricMachines, "electricMachines"),
                Objects.requireNonNull(configurableMachines, "configurableMachines"),
                Objects.requireNonNull(energyService, "energyService"),
                Objects.requireNonNull(cargoService, "cargoService"),
                Objects.requireNonNull(decorationService, "decorationService"),
                Objects.requireNonNull(gpsService, "gpsService"),
                Objects.requireNonNull(ancientAltarService, "ancientAltarService"),
                Objects.requireNonNull(androidService, "androidService"),
                Objects.requireNonNull(spawnerService, "spawnerService"),
                Objects.requireNonNull(blockPlacerService, "blockPlacerService"),
                Objects.requireNonNull(infusedHopperService, "infusedHopperService"),
                Objects.requireNonNull(hologramProjectorService, "hologramProjectorService"),
                machineRuntime,
                LOGGER);
        this.placementRouter = new SfxBlockPlacementRouter(
                this.items,
                this.blockData,
                basicMachines,
                electricMachines,
                configurableMachines,
                energyService,
                cargoService,
                decorationService,
                gpsService,
                ancientAltarService,
                androidService,
                spawnerService,
                blockPlacerService,
                infusedHopperService,
                hologramProjectorService,
                runtime,
                machineRuntime,
                LOGGER);
        this.explosionService = new SfxBlockExplosionService(this.blockData, this.lifecycleRouter, machineRuntime);
        this.environmentalGuard = new SfxAnchoredBlockEnvironmentalGuard(this.blockData, runtime);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placementRouter.handlePlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnchoredAxeInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (blockData.findAnchor(event.getClickedBlock().getLocation()).isEmpty()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && event.getClickedBlock().getType() == Material.JUKEBOX
                && item.getType().name().startsWith("MUSIC_DISC_")) {
            denyVanillaBlockMutation(event);
            return;
        }
        if (item == null || !isAxe(item.getType())) {
            return;
        }
        denyVanillaHeldItemMutation(event);
    }

    private static void denyVanillaBlockMutation(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    private static void denyVanillaHeldItemMutation(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
    }

    private boolean isAxe(Material material) {
        return switch (material) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
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
        event.setDropItems(false);
        boolean containment = items.readMarker(event.getPlayer().getInventory().getItemInMainHand())
                .map(marker -> "sf:pickaxe_of_containment".equals(marker.itemId()))
                .orElse(false);
        lifecycleRouter.destroyAnchoredBlock(event.getBlock(), instance.instanceId(), instance.typeId(), new SfxBlockDestructionOptions(containment));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explosionService.handleEntityExplode(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosionService.handleBlockExplode(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        explosionService.handleEntityChangeBlock(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        environmentalGuard.handlePistonExtend(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        environmentalGuard.handlePistonRetract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        environmentalGuard.handleFluidFlow(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        environmentalGuard.handleBlockPhysics(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        environmentalGuard.handleBucketEmpty(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        environmentalGuard.handleSpongeAbsorb(event);
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
}
