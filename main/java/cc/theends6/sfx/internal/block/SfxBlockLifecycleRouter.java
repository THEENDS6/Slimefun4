package cc.theends6.sfx.internal.block;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.core.SfxResult;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.machine.SfxMachinePhase;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachineStatus;
import cc.theends6.sfx.internal.machine.SfxMachineTickContext;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;








public final class SfxBlockLifecycleRouter {
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
    private final SfxMachineRuntimeEngine machineRuntime;
    private final Logger logger;

    public SfxBlockLifecycleRouter(
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
            SfxMachineRuntimeEngine machineRuntime,
            Logger logger
    ) {
        this.items = items;
        this.blockData = blockData;
        this.basicMachines = basicMachines;
        this.electricMachines = electricMachines;
        this.configurableMachines = configurableMachines;
        this.energyService = energyService;
        this.cargoService = cargoService;
        this.decorationService = decorationService;
        this.gpsService = gpsService;
        this.ancientAltarService = ancientAltarService;
        this.androidService = androidService;
        this.spawnerService = spawnerService;
        this.blockPlacerService = blockPlacerService;
        this.infusedHopperService = infusedHopperService;
        this.hologramProjectorService = hologramProjectorService;
        this.machineRuntime = machineRuntime;
        this.logger = logger == null ? Logger.getLogger("SlimeFunX") : logger;
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        runFrameworkBreakPhase(block, instanceId, typeId);
        if (typeId == null) return;
        if (basicMachines.supportsType(typeId)) {
            basicMachines.destroyAnchoredBlock(block, typeId);
        } else if (electricMachines.supportsType(typeId)) {
            electricMachines.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (configurableMachines.supportsType(typeId)) {
            configurableMachines.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (energyService.supportsType(typeId)) {
            energyService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (cargoService.supportsType(typeId)) {
            cargoService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (gpsService.supportsType(typeId)) {
            gpsService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (decorationService.supportsType(typeId)) {
            decorationService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (ancientAltarService.supportsType(typeId)) {
            ancientAltarService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (androidService.supportsType(typeId)) {
            androidService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (spawnerService.supportsType(typeId)) {
            spawnerService.destroyAnchoredBlock(block, instanceId, typeId, false);
        } else if (blockPlacerService.supportsType(typeId)) {
            blockPlacerService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (infusedHopperService.supportsType(typeId)) {
            infusedHopperService.destroyAnchoredBlock(block, instanceId, typeId);
        } else if (hologramProjectorService.supportsType(typeId)) {
            hologramProjectorService.destroyAnchoredBlock(block, instanceId, typeId);
        } else {
            commitGenericDestruction(block, instanceId, typeId);
        }
    }

    private void runFrameworkBreakPhase(Block block, UUID instanceId, String typeId) {
        if (machineRuntime == null) return;
        machineRuntime.runPhase(typeId, SfxMachinePhase.ON_BREAK, instanceId, block == null ? null : block.getLocation(), new SfxMachineTickContext(0L, 1L, false), null, SfxMachineStatus.IDLE);
        machineRuntime.forget(instanceId);
    }

    private void commitGenericDestruction(Block block, UUID instanceId, String typeId) {
        if (block == null || typeId == null) {
            return;
        }
        SfxBlockBehaviorRegistry registry = new SfxBlockBehaviorRegistry();
        registry.register(new SfxBlockBehavior() {
            @Override
            public String typeId() {
                return typeId;
            }

            @Override
            public SfxResult<Void> beforeBreak(SfxBlockBreakContext context, SfxAnchorRecord anchor, SfxBlockInstanceRecord instance) {
                dropStoredContents(block);
                SfxBlockDrops.dropPluginBlock(block, items, typeId);
                return SfxResult.ok();
            }

            @Override
            public void afterBreak(SfxBlockBreakContext context, SfxAnchorRecord anchor, SfxBlockInstanceRecord instance) {
                if (machineRuntime != null) machineRuntime.forget(instanceId);
            }
        });
        SfxBlockDestructionTransaction transaction = new SfxBlockDestructionTransaction(blockData, registry, logger);
        SfxResult<Void> result = transaction.commit(new SfxBlockBreakContext(block.getLocation(), null, SfxBlockDestructionCause.UNKNOWN));
        if (!result.success()) {
            result.cause().ifPresentOrElse(
                    cause -> logger.log(Level.WARNING, "Failed to destroy generic SFX block " + typeId + " at " + block.getLocation(), cause),
                    () -> logger.warning("Failed to destroy generic SFX block " + typeId + " at " + block.getLocation() + ": " + result.message()));
        }
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
