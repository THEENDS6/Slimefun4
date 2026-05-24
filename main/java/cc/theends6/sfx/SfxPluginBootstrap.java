package cc.theends6.sfx;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.bootstrap.BaseContentBootstrap;
import cc.theends6.sfx.internal.altar.SfxAncientAltarService;
import cc.theends6.sfx.internal.bootstrap.SfxYamlContentLoader;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBasicMachineBlockListener;
import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.machine.SfxIndustrialMinerService;
import cc.theends6.sfx.internal.listener.SfxAncientRuneEffectListener;
import cc.theends6.sfx.internal.block.SfxHologramProjectorService;
import cc.theends6.sfx.internal.block.SfxInfusedHopperService;
import cc.theends6.sfx.internal.block.SfxBlockPlacerService;
import cc.theends6.sfx.internal.block.SfxSpawnerService;
import cc.theends6.sfx.internal.block.SfxBlockPersistenceListener;
import cc.theends6.sfx.internal.block.SqliteSfxBlockDataRepository;
import cc.theends6.sfx.internal.command.SfxCommand;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
import cc.theends6.sfx.internal.core.SfxAuditReport;
import cc.theends6.sfx.internal.core.SfxAuditSink;
import cc.theends6.sfx.internal.core.SfxListenerRegistrar;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
import cc.theends6.sfx.internal.android.SfxAndroidService;
import cc.theends6.sfx.internal.android.SqliteSfxAndroidScriptRepository;
import cc.theends6.sfx.internal.gps.SqliteSfxGpsDataRepository;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayService;
import cc.theends6.sfx.internal.display.SfxFloatingTextDisplayListener;
import cc.theends6.sfx.internal.electric.SfxElectricMachineService;
import cc.theends6.sfx.internal.energy.SfxEnergyService;
import cc.theends6.sfx.internal.energy.SfxMultimeterListener;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.listener.SfxArmorEffectListener;
import cc.theends6.sfx.internal.listener.SfxBackpackListener;
import cc.theends6.sfx.internal.listener.SfxGuideListener;
import cc.theends6.sfx.internal.listener.SfxItemUseDispatcher;
import cc.theends6.sfx.internal.listener.SfxLegacyCombatToolListener;
import cc.theends6.sfx.internal.listener.SfxLegacyFoodListener;
import cc.theends6.sfx.internal.listener.SfxPlayerProfileListener;
import cc.theends6.sfx.internal.listener.SfxResearchFireworksListener;
import cc.theends6.sfx.internal.listener.SfxSoulboundListener;
import cc.theends6.sfx.internal.listener.SfxTalismanListener;
import cc.theends6.sfx.internal.listener.SfxLegacyUtilityListener;
import cc.theends6.sfx.internal.listener.SfxVanillaGuardListener;
import cc.theends6.sfx.internal.machine.ManualMachineService;
import cc.theends6.sfx.internal.machine.SfxMachineCategory;
import cc.theends6.sfx.internal.machine.SfxMachineBuiltinEffectHooks;
import cc.theends6.sfx.internal.machine.SfxMachineDomainEffectHooks;
import cc.theends6.sfx.internal.machine.SfxMachineFrameworkCatalog;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.machine.SfxMachinePhaseLedger;
import cc.theends6.sfx.internal.machine.SfxManualMachineDeployListener;
import cc.theends6.sfx.internal.machine.SfxManualMachineListener;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.playerdata.SqliteSfxPlayerDataRepository;
import cc.theends6.sfx.internal.recipe.DefaultSfxRecipeRegistry;
import cc.theends6.sfx.internal.recipe.SfxRecipeYamlLoader;
import cc.theends6.sfx.internal.research.SfxResearchRegistry;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.radiation.SfxRadiationService;
import cc.theends6.sfx.internal.technical.SfxTechnicalGadgetService;
import cc.theends6.sfx.internal.research.SfxResearchYamlLoader;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.io.File;
import java.util.Objects;
import java.lang.reflect.Method;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;









final class SfxPluginBootstrap {
    private SfxPluginBootstrap() {
    }

    static void enable(SlimeFunXPlugin plugin) {
        if (plugin.packetEventsUnavailable || !plugin.packetEventsLoaded) {
            if (!plugin.packetEventsUnavailable) {
                plugin.logPacketEventsStartupFailure(new IllegalStateException("PacketEvents was not initialized during onLoad"));
            }
            plugin.getLogger().severe("SlimeFunX is disabling because PacketEvents is not available or failed to initialize.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        if (plugin.machineRuntime != null) {
            plugin.machineRuntime.clear();
        }
        if (plugin.packetEventsLoaded) {
            try {
                plugin.invokePacketEventsApi("init");
            } catch (Throwable throwable) {
                plugin.logPacketEventsStartupFailure(throwable);
                plugin.getServer().getPluginManager().disablePlugin(plugin);
                return;
            }
        }
        plugin.saveDefaultConfig();
        plugin.syncBundledLanguages();
        var runtime = new cc.theends6.sfx.internal.runtime.PaperSfxRuntime(plugin);
        try {
            plugin.playerDataService = new SfxPlayerDataService(plugin, runtime, new SqliteSfxPlayerDataRepository(plugin, plugin.playerDataFile()));
            plugin.playerDataService.initialize();
            plugin.blockDataService = new SfxBlockDataService(plugin, runtime, new SqliteSfxBlockDataRepository(plugin, plugin.blockDataFile()));
            plugin.blockDataService.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SFX persistent storage", exception);
        }
        plugin.researchRegistry = new SfxResearchRegistry();
        plugin.researchService = new SfxResearchService(plugin.researchRegistry, plugin.playerDataService);

        plugin.localization = new SfxLocalization(plugin);
        plugin.legacyItemBehaviorConfig = new SfxLegacyItemBehaviorConfig(plugin);
        plugin.legacyItemBehaviorConfig.ensureDefaultFile();
        plugin.legacyItemBehaviorConfig.reload();
        plugin.api = SfxApiImpl.bootstrap(plugin, plugin.localization, plugin.playerDataService, plugin.researchService);

        plugin.bootstrapContent();

        plugin.machineRuntime = new SfxMachineRuntimeEngine();
        plugin.machinePhaseLedger = new SfxMachinePhaseLedger();
        plugin.machineRuntime.registerPhaseObserver(plugin.machinePhaseLedger);
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

        SfxPluginListenerWiring.register(plugin, manualMachineService, placeableBlockListener);
        plugin.decorationService.start();
        plugin.ancientAltarService.start();
        plugin.infusedHopperService.start();
        plugin.hologramProjectorService.rebuildIndex();
        plugin.radiationService.start();
        plugin.androidService.start();

        SfxCommand command = new SfxCommand(plugin, plugin.api);
        PluginCommand pluginCommand = Objects.requireNonNull(plugin.getCommand("slimefunx"), "plugin.yml missing /slimefunx command");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        plugin.getLogger().info("SFX enabled. Registered " + plugin.api.itemRegistry().items().size()
                + " item definitions and " + plugin.api.manualMachines().machines().size() + " manual machines. "
                + "Registered " + plugin.listenerRegistrar.registered().size() + " listeners through sfx-core. "
                + "Machine framework definitions: " + plugin.machineRuntime.definitionCount() + " (" + frameworkStats.frameworkCatalogExtras() + " catalog extras), "
                + plugin.machineRuntime.capabilityDeclarationCount() + " capabilities, "
                + plugin.machineRuntime.policyRefCount() + " policy refs, "
                + plugin.machineRuntime.effectCount() + " phase effects, "
                + plugin.machineRuntime.effectHookCount() + " bound effect hooks (" + frameworkStats.builtinEffectHooks() + " built-in defaults, " + frameworkStats.domainEffectHooks() + " domain hooks, " + frameworkStats.genericEffectHooks() + " generic fallbacks), "
                + plugin.machineRuntime.phaseObserverCount() + " phase observers, "
                + plugin.machineRuntime.unboundDeclaredEffectNames().size() + " unbound declared effects. "
                + "Loaded " + plugin.blockDataService.anchorCount() + " block anchors and "
                + plugin.blockDataService.instanceCount() + " block instances.");
    
    }

    static void disable(SlimeFunXPlugin plugin) {
        SfxPluginShutdown.disable(plugin);
    }
}
