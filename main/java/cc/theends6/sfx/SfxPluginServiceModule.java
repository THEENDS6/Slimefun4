package cc.theends6.sfx;

import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.android.SqliteSfxAndroidScriptRepository;
import cc.theends6.sfx.internal.block.SfxBasicMachineBlockListener;
import cc.theends6.sfx.internal.block.SfxBlockPersistenceListener;
import cc.theends6.sfx.internal.block.SfxBlockPlacerService;
import cc.theends6.sfx.internal.block.SfxHologramProjectorService;
import cc.theends6.sfx.internal.block.SfxInfusedHopperService;
import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.block.SfxSpawnerService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.core.SfxListenerRegistrar;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.gps.SqliteSfxGpsDataRepository;
import cc.theends6.sfx.internal.machine.ManualMachineService;
import cc.theends6.sfx.internal.machine.SfxIndustrialMinerService;
import cc.theends6.sfx.internal.radiation.SfxRadiationService;
import cc.theends6.sfx.internal.technical.SfxTechnicalGadgetService;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;

/**
 * Constructs domain services and their lifecycle-facing listener adapters.
 */
final class SfxPluginServiceModule {
    private SfxPluginServiceModule() {
    }

    static SfxPluginServices create(SlimeFunXPlugin plugin) {
        plugin.basicMachineBlockListener = new SfxBasicMachineBlockListener(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.machineRuntime);
        ManualMachineService manualMachineService = new ManualMachineService(plugin, plugin.api.runtime(), plugin.api.internalManualMachines(), plugin.api.items(), plugin.localization, plugin.basicMachineBlockListener);
        plugin.floatingTextDisplayService = new SfxFloatingTextDisplayService(plugin, plugin.api.runtime());
        plugin.virtualContainerService = new SfxVirtualContainerService(plugin, plugin.api.runtime());
        plugin.electricMachineService = new SfxElectricMachineService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.playerDataService, plugin.api.internalManualMachines(), plugin.virtualContainerService, plugin.floatingTextDisplayService, plugin.machineRuntime);
        plugin.configurableMachineService = new SfxConfigurableMachineService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.floatingTextDisplayService, plugin.machineRuntime);
        plugin.technicalGadgetService = new SfxTechnicalGadgetService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization);
        plugin.energyService = new SfxEnergyService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.electricMachineService, plugin.configurableMachineService, plugin.floatingTextDisplayService, plugin.technicalGadgetService.rechargeableItems(), plugin.machineRuntime);
        plugin.cargoService = new SfxCargoService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.virtualContainerService, plugin.floatingTextDisplayService, plugin.electricMachineService, plugin.machineRuntime);
        plugin.decorationService = new SfxDecorationService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.blockDataService, plugin.machineRuntime);
        plugin.gpsService = new SfxGpsService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.api.menus(), plugin.localization, plugin.blockDataService, plugin.decorationService, plugin.electricMachineService, new SqliteSfxGpsDataRepository(plugin, plugin.gpsDataFile()), plugin.machineRuntime);
        plugin.androidService = new SfxAndroidService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.api.itemRegistry(), plugin.localization, plugin.blockDataService, new SqliteSfxAndroidScriptRepository(plugin, plugin.androidScriptsFile()), plugin.machineRuntime);
        plugin.ancientAltarService = new SfxAncientAltarService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.api.itemRegistry(), plugin.localization, plugin.blockDataService, plugin.machineRuntime);
        plugin.spawnerService = new SfxSpawnerService(plugin, plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.machineRuntime);
        plugin.infusedHopperService = new SfxInfusedHopperService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.blockDataService, plugin.machineRuntime);
        plugin.hologramProjectorService = new SfxHologramProjectorService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.floatingTextDisplayService, plugin.machineRuntime);
        plugin.blockPlacerService = new SfxBlockPlacerService(plugin.api.runtime(), plugin.api.items(), plugin.blockDataService, plugin.spawnerService, plugin.hologramProjectorService, plugin.infusedHopperService, plugin.machineRuntime);
        plugin.industrialMinerService = new SfxIndustrialMinerService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.localization, plugin.blockDataService, plugin.machineRuntime);

        SfxPluginFrameworkWiring.Stats frameworkStats = SfxPluginFrameworkWiring.wire(plugin);
        SfxPlaceableBlockListener placeableBlockListener = new SfxPlaceableBlockListener(plugin.api.items(), plugin.blockDataService, plugin.basicMachineBlockListener, plugin.electricMachineService, plugin.configurableMachineService, plugin.energyService, plugin.cargoService, plugin.decorationService, plugin.gpsService, plugin.ancientAltarService, plugin.androidService, plugin.spawnerService, plugin.blockPlacerService, plugin.infusedHopperService, plugin.hologramProjectorService, plugin.api.runtime(), plugin.machineRuntime);
        plugin.blockPersistenceListener = new SfxBlockPersistenceListener(plugin, plugin.api.runtime(), plugin.blockDataService, plugin.gpsService);
        plugin.radiationService = new SfxRadiationService(plugin, plugin.api.runtime(), plugin.api.items(), plugin.api.itemRegistry(), plugin.playerDataService);
        plugin.listenerRegistrar = new SfxListenerRegistrar(plugin);
        plugin.moduleManager = SfxPluginLifecycleWiring.create(plugin);
        return new SfxPluginServices(manualMachineService, placeableBlockListener, frameworkStats, plugin.moduleManager);
    }
}
