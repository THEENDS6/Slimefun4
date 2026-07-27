package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.api.block.SfxBlockTransformDecision;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.addon.SfxAddonBlockLifecycleService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.api.text.Text;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
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
    private final SfxAddonBlockLifecycleService addonLifecycle;
    private final SfxRuntime runtime;
    private final SfxLocalization localization;

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
            SfxMachineRuntimeEngine machineRuntime,
            cc.theends6.sfx.internal.addon.SfxAddonManager addonManager,
            SfxLocalization localization
    ) {
        this.items = Objects.requireNonNull(items, "items");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
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
                addonManager,
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
        blockPlacerService.bindPlacementRouter(this.placementRouter);
        androidService.bindBlockLifecycleRouter(this.lifecycleRouter);
        this.explosionService = new SfxBlockExplosionService(this.blockData, this.lifecycleRouter, machineRuntime);
        this.environmentalGuard = new SfxAnchoredBlockEnvironmentalGuard(this.blockData, runtime);
        this.addonLifecycle = new SfxAddonBlockLifecycleService(this.blockData, addonManager, LOGGER);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String itemId = items.readMarker(event.getItemInHand()).map(marker -> marker.itemId()).orElse(null);
        if (itemId != null && !items.canUse(event.getPlayer(), itemId)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.prefixed(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    localization.text("messages.no-item-permission")));
            return;
        }
        placementRouter.handlePlace(event);
        if (!event.isCancelled() && !addonLifecycle.initializePlaced(event.getBlockPlaced(), event.getPlayer())) {
            blockData.unregisterAt(event.getBlockPlaced().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAddonInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) addonLifecycle.onInteract(event.getClickedBlock(), event.getPlayer());
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
        lifecycleRouter.destroyAnchoredBlock(event.getBlock(), instance.instanceId(), instance.typeId(),
                new SfxBlockDestructionOptions(containment, SfxBlockDestructionCause.PLAYER_BREAK, event.getPlayer()));
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
        addonLifecycle.onPistonMove(event.getBlocks());
        environmentalGuard.handlePistonExtend(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        addonLifecycle.onPistonMove(event.getBlocks());
        environmentalGuard.handlePistonRetract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Block target = event.getToBlock();
        SfxBlockInstanceRecord instance = addonLifecycle.managedInstance(target).orElse(null);
        if (instance != null) {
            event.setCancelled(true);
            lifecycleRouter.destroyAnchoredBlock(target, instance.instanceId(), instance.typeId(),
                    new SfxBlockDestructionOptions(false, SfxBlockDestructionCause.FLUID_BREAK, null));
            target.setType(Material.AIR, false);
            return;
        }
        environmentalGuard.handleFluidFlow(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        addonLifecycle.onPhysics(event.getBlock());
        addonLifecycle.onNeighborUpdate(event.getBlock());
        environmentalGuard.handleBlockPhysics(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        environmentalGuard.handleBucketEmpty(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (handleAddonTransform(event, event.getBlock(), Material.AIR)) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (handleAddonTransform(event, event.getBlock(), event.getNewState().getType())) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (handleAddonTransform(event, event.getBlock(), event.getNewState().getType())) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (handleAddonTransform(event, event.getBlock(), event.getNewState().getType())) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (handleAddonTransform(event, event.getBlock(), event.getNewState().getType())) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (handleAddonTransform(event, event.getBlock(), event.getNewState().getType())) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (handleAddonTransform(event, event.getBlock(), Material.AIR)) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (handleAddonTransform(event, event.getBlock(), Material.FIRE)) return;
        environmentalGuard.cancelIfAnchored(event, event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        environmentalGuard.handleSpongeAbsorb(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        addonLifecycle.onLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        addonLifecycle.onUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        addonLifecycle.onWorldUnload(event.getWorld());
    }

    private boolean handleAddonTransform(org.bukkit.event.Cancellable event, Block block, Material to) {
        SfxBlockTransformDecision decision = addonLifecycle.onVanillaTransform(block, to).orElse(null);
        if (decision == null) return false;
        if (decision.action() == SfxBlockTransformDecision.Action.ALLOW) {
            Location anchor = block.getLocation();
            runtime.executeAtLater(anchor, 1L,
                    () -> addonLifecycle.reconcileAllowedTransform(anchor.getBlock(), to));
            return true;
        }
        event.setCancelled(true);
        if (decision.action() == SfxBlockTransformDecision.Action.REPLACE_WITH_CUSTOM_BLOCK) {
            addonLifecycle.replaceWithCustom(block, null, decision);
        }
        return true;
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
