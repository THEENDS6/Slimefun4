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
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
import cc.theends6.sfx.internal.decoration.SfxDecorationService;
import cc.theends6.sfx.internal.gps.SfxGpsService;
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
import cc.theends6.sfx.internal.listener.SfxDebugJoinListener;
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

public final class SlimeFunXPlugin extends JavaPlugin {

    private SfxApiImpl api;
    private SfxLocalization localization;
    private SfxLegacyItemBehaviorConfig legacyItemBehaviorConfig;
    private SfxPlayerDataService playerDataService;
    private SfxBlockDataService blockDataService;
    private SfxBlockPersistenceListener blockPersistenceListener;
    private SfxResearchRegistry researchRegistry;
    private SfxResearchService researchService;
    private SfxBackpackListener backpackListener;
    private SfxBasicMachineBlockListener basicMachineBlockListener;
    private SfxElectricMachineService electricMachineService;
    private SfxConfigurableMachineService configurableMachineService;
    private SfxEnergyService energyService;
    private SfxVirtualContainerService virtualContainerService;
    private SfxCargoService cargoService;
    private SfxDecorationService decorationService;
    private SfxGpsService gpsService;
    private SfxRadiationService radiationService;
    private SfxTechnicalGadgetService technicalGadgetService;
    private SfxFloatingTextDisplayService floatingTextDisplayService;
    private SfxAncientAltarService ancientAltarService;
    private SfxSpawnerService spawnerService;
    private SfxBlockPlacerService blockPlacerService;
    private SfxInfusedHopperService infusedHopperService;
    private SfxHologramProjectorService hologramProjectorService;
    private SfxIndustrialMinerService industrialMinerService;
    private Object packetEventsApi;
    private boolean packetEventsLoaded;
    private boolean packetEventsUnavailable;

    @Override
    public void onLoad() {
        try {
            Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents", true, getClassLoader());
            Class<?> builderClass = Class.forName("io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder", true, getClassLoader());
            Object api = invokeSingleArgStaticReturning(builderClass, "build", this);
            invokeSingleArgStatic(packetEventsClass, "setAPI", api);
            packetEventsApi = packetEventsClass.getMethod("getAPI").invoke(null);
            invokePacketEventsApi("load");
            packetEventsLoaded = true;
        } catch (Throwable throwable) {
            packetEventsUnavailable = true;
            logPacketEventsStartupFailure(throwable);
        }
    }

    @Override
    public void onEnable() {
        if (packetEventsUnavailable || !packetEventsLoaded) {
            if (!packetEventsUnavailable) {
                logPacketEventsStartupFailure(new IllegalStateException("PacketEvents was not initialized during onLoad"));
            }
            getLogger().severe("SlimeFunX is disabling because PacketEvents is not available or failed to initialize.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (packetEventsLoaded) {
            try {
                invokePacketEventsApi("init");
            } catch (Throwable throwable) {
                logPacketEventsStartupFailure(throwable);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        saveDefaultConfig();
        syncBundledLanguages();
        saveBundledTestingFile();

        var runtime = new cc.theends6.sfx.internal.runtime.PaperSfxRuntime(this);
        try {
            this.playerDataService = new SfxPlayerDataService(this, runtime, new SqliteSfxPlayerDataRepository(this, playerDataFile()));
            playerDataService.initialize();
            this.blockDataService = new SfxBlockDataService(this, runtime, new SqliteSfxBlockDataRepository(this, blockDataFile()));
            blockDataService.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SFX persistent storage", exception);
        }
        this.researchRegistry = new SfxResearchRegistry();
        this.researchService = new SfxResearchService(researchRegistry, playerDataService);

        this.localization = new SfxLocalization(this);
        this.legacyItemBehaviorConfig = new SfxLegacyItemBehaviorConfig(this);
        legacyItemBehaviorConfig.ensureDefaultFile();
        legacyItemBehaviorConfig.reload();
        this.api = SfxApiImpl.bootstrap(this, localization, playerDataService, researchService);

        bootstrapContent();

        this.basicMachineBlockListener = new SfxBasicMachineBlockListener(this, api.runtime(), api.items(), localization, blockDataService);
        ManualMachineService manualMachineService = new ManualMachineService(this, api.runtime(), api.internalManualMachines(), api.items(), localization, basicMachineBlockListener);
        this.floatingTextDisplayService = new SfxFloatingTextDisplayService(this, api.runtime());
        this.virtualContainerService = new SfxVirtualContainerService(this, api.runtime());
        this.electricMachineService = new SfxElectricMachineService(this, api.runtime(), api.items(), localization, blockDataService, playerDataService, api.internalManualMachines(), virtualContainerService, floatingTextDisplayService);
        this.configurableMachineService = new SfxConfigurableMachineService(this, api.runtime(), api.items(), localization, blockDataService, floatingTextDisplayService);
        this.technicalGadgetService = new SfxTechnicalGadgetService(this, api.runtime(), api.items(), localization);
        this.energyService = new SfxEnergyService(this, api.runtime(), api.items(), localization, blockDataService, electricMachineService, configurableMachineService, floatingTextDisplayService, technicalGadgetService.rechargeableItems());
        this.cargoService = new SfxCargoService(this, api.runtime(), api.items(), localization, blockDataService, virtualContainerService, floatingTextDisplayService);
        this.decorationService = new SfxDecorationService(this, api.runtime(), api.items(), blockDataService);
        this.gpsService = new SfxGpsService(this, api.runtime(), api.items(), api.menus(), localization, blockDataService, decorationService, electricMachineService, new SqliteSfxGpsDataRepository(this, gpsDataFile()));
        this.ancientAltarService = new SfxAncientAltarService(this, api.runtime(), api.items(), api.itemRegistry(), localization, blockDataService);
        this.spawnerService = new SfxSpawnerService(this, api.items(), localization, blockDataService);
        this.infusedHopperService = new SfxInfusedHopperService(this, api.runtime(), api.items(), blockDataService);
        this.hologramProjectorService = new SfxHologramProjectorService(this, api.runtime(), api.items(), localization, blockDataService, floatingTextDisplayService);
        this.blockPlacerService = new SfxBlockPlacerService(api.runtime(), api.items(), blockDataService, spawnerService, hologramProjectorService, infusedHopperService);
        this.industrialMinerService = new SfxIndustrialMinerService(this, api.runtime(), api.items(), localization, blockDataService);
        SfxPlaceableBlockListener placeableBlockListener = new SfxPlaceableBlockListener(api.items(), blockDataService, basicMachineBlockListener, electricMachineService, configurableMachineService, energyService, cargoService, decorationService, gpsService, ancientAltarService, spawnerService, blockPlacerService, infusedHopperService, hologramProjectorService, api.runtime());
        this.blockPersistenceListener = new SfxBlockPersistenceListener(this, api.runtime(), blockDataService, gpsService);
        this.radiationService = new SfxRadiationService(this, api.runtime(), api.items(), api.itemRegistry(), playerDataService);

        this.backpackListener = new SfxBackpackListener(this, api.runtime(), api.items(), localization, playerDataService, researchService);
        SfxLegacyUtilityListener utilityListener = new SfxLegacyUtilityListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig, blockDataService, radiationService, playerDataService, researchService);
        SfxLegacyCombatToolListener combatToolListener = new SfxLegacyCombatToolListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig, blockDataService);
        SfxLegacyFoodListener foodListener = new SfxLegacyFoodListener(this, api.runtime(), api.items(), localization);
        SfxTalismanListener talismanListener = new SfxTalismanListener(this, api.runtime(), api.items(), researchService, legacyItemBehaviorConfig.talismans());

        getServer().getPluginManager().registerEvents(api.menus(), this);
        getServer().getPluginManager().registerEvents(new SfxFloatingTextDisplayListener(floatingTextDisplayService), this);
        getServer().getPluginManager().registerEvents(new SfxPlayerProfileListener(playerDataService), this);
        getServer().getPluginManager().registerEvents(radiationService, this);
        getServer().getPluginManager().registerEvents(new SfxGuideListener(this, api.items(), api.guide()), this);
        getServer().getPluginManager().registerEvents(new SfxItemUseDispatcher(api.items(), backpackListener, utilityListener, combatToolListener, foodListener, researchService, localization), this);
        getServer().getPluginManager().registerEvents(new SfxManualMachineListener(manualMachineService, api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxManualMachineDeployListener(this, api.internalManualMachines(), localization, blockDataService), this);
        getServer().getPluginManager().registerEvents(new SfxMultimeterListener(this, api.items(), localization, blockDataService, electricMachineService, configurableMachineService, energyService), this);
        getServer().getPluginManager().registerEvents(placeableBlockListener, this);
        getServer().getPluginManager().registerEvents(blockPersistenceListener, this);
        getServer().getPluginManager().registerEvents(basicMachineBlockListener, this);
        getServer().getPluginManager().registerEvents(electricMachineService, this);
        getServer().getPluginManager().registerEvents(configurableMachineService, this);
        getServer().getPluginManager().registerEvents(energyService, this);
        getServer().getPluginManager().registerEvents(virtualContainerService, this);
        getServer().getPluginManager().registerEvents(cargoService, this);
        getServer().getPluginManager().registerEvents(decorationService, this);
        getServer().getPluginManager().registerEvents(gpsService, this);
        getServer().getPluginManager().registerEvents(ancientAltarService, this);
        getServer().getPluginManager().registerEvents(blockPlacerService, this);
        getServer().getPluginManager().registerEvents(infusedHopperService, this);
        getServer().getPluginManager().registerEvents(hologramProjectorService, this);
        getServer().getPluginManager().registerEvents(industrialMinerService, this);
        getServer().getPluginManager().registerEvents(technicalGadgetService, this);
        getServer().getPluginManager().registerEvents(backpackListener, this);
        getServer().getPluginManager().registerEvents(utilityListener, this);
        getServer().getPluginManager().registerEvents(combatToolListener, this);
        getServer().getPluginManager().registerEvents(foodListener, this);
        getServer().getPluginManager().registerEvents(talismanListener, this);
        getServer().getPluginManager().registerEvents(new SfxAncientRuneEffectListener(this, api.runtime(), api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxSoulboundListener(this, api.items(), researchService), this);
        getServer().getPluginManager().registerEvents(new SfxResearchFireworksListener(), this);
        getServer().getPluginManager().registerEvents(new SfxVanillaGuardListener(this, api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxArmorEffectListener(api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxDebugJoinListener(this, api.runtime(), localization), this);
        decorationService.start();
        ancientAltarService.start();
        infusedHopperService.start();
        hologramProjectorService.rebuildIndex();
        radiationService.start();

        SfxCommand command = new SfxCommand(this, api);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("slimefunx"), "plugin.yml missing /slimefunx command");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info("SFX enabled. Registered " + api.itemRegistry().items().size()
                + " item definitions and " + api.manualMachines().machines().size() + " manual machines. "
                + "Loaded " + blockDataService.anchorCount() + " block anchors and "
                + blockDataService.instanceCount() + " block instances.");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            api.menus().closeAll();
        }
        if (blockPersistenceListener != null) {
            blockPersistenceListener.shutdown();
        }
        if (backpackListener != null) {
            backpackListener.shutdown();
        }
        if (basicMachineBlockListener != null) {
            basicMachineBlockListener.shutdown();
        }
        if (electricMachineService != null) {
            electricMachineService.shutdown();
        }
        if (configurableMachineService != null) {
            configurableMachineService.shutdown();
        }
        if (cargoService != null) {
            cargoService.shutdown();
        }
        if (gpsService != null) {
            gpsService.shutdown();
        }
        if (decorationService != null) {
            decorationService.shutdown();
        }
        if (ancientAltarService != null) {
            ancientAltarService.shutdown();
        }
        if (infusedHopperService != null) {
            infusedHopperService.shutdown();
        }
        if (industrialMinerService != null) {
            industrialMinerService.shutdown();
        }
        if (virtualContainerService != null) {
            virtualContainerService.shutdown();
        }
        if (energyService != null) {
            energyService.shutdown();
        }
        if (technicalGadgetService != null) {
            technicalGadgetService.shutdown();
        }
        if (floatingTextDisplayService != null) {
            floatingTextDisplayService.shutdown();
        }
        if (radiationService != null) {
            radiationService.shutdown();
        }
        if (playerDataService != null) {
            playerDataService.shutdown();
        }
        if (blockDataService != null) {
            blockDataService.shutdown();
        }
        if (packetEventsLoaded) {
            try {
                invokePacketEventsApi("terminate");
            } catch (Throwable throwable) {
                getLogger().warning("Failed to terminate PacketEvents cleanly: " + throwable.getMessage());
            }
            packetEventsLoaded = false;
            packetEventsApi = null;
        }
    }

    public SfxApi api() {
        return api;
    }

    public SfxLocalization localization() {
        return localization;
    }

    public SfxLegacyItemBehaviorConfig legacyItemBehaviorConfig() {
        return legacyItemBehaviorConfig;
    }

    public SfxPlayerDataService playerDataService() {
        return playerDataService;
    }

    public SfxBlockDataService blockDataService() {
        return blockDataService;
    }

    public SfxResearchService researchService() {
        return researchService;
    }

    public SfxBackpackListener backpackListener() {
        return backpackListener;
    }

    public synchronized void reloadAllContent() {
        reloadConfig();
        syncBundledLanguages();
        localization.reload();
        legacyItemBehaviorConfig.reload();
        saveBundledTestingFile();
        api.menus().closeAll();
        if (backpackListener != null) {
            backpackListener.shutdown();
        }
        bootstrapContent();
        if (ancientAltarService != null) {
            ancientAltarService.reloadRecipes();
            ancientAltarService.rebuildIndex();
        }
        if (hologramProjectorService != null) {
            hologramProjectorService.rebuildIndex();
        }
    }

    private void bootstrapContent() {
        DefaultSfxItemRegistry itemRegistry = (DefaultSfxItemRegistry) api.itemRegistry();
        itemRegistry.clear();
        api.internalManualMachines().clear();

        BaseContentBootstrap.register(itemRegistry, api.internalManualMachines());
        SfxYamlContentLoader yamlContentLoader = new SfxYamlContentLoader(this, itemRegistry);
        yamlContentLoader.ensureDefaultFiles(syncBundledItemFiles());
        yamlContentLoader.registerAll();

        SfxRecipeYamlLoader recipeYamlLoader = new SfxRecipeYamlLoader(this);
        recipeYamlLoader.ensureDefaultFiles(syncBundledRecipeFiles());
        DefaultSfxRecipeRegistry recipeRegistry = new DefaultSfxRecipeRegistry();
        recipeYamlLoader.loadInto(recipeRegistry);
        DefaultSfxRecipeRegistry.AuditResult recipeAudit = recipeRegistry.apply(itemRegistry, api.internalManualMachines());
        BaseContentBootstrap.syncManualMachineGuideContent(itemRegistry, api.internalManualMachines());
        SfxResearchYamlLoader researchYamlLoader = new SfxResearchYamlLoader(this);
        researchYamlLoader.ensureDefaultFiles(syncBundledResearchFiles());
        researchYamlLoader.loadInto(researchRegistry);

        if (getConfig().getBoolean("debug-text.enabled", false)) {
            getLogger().info(recipeAudit.summary());
            recipeAudit.warnings().stream().limit(80).forEach(warning -> getLogger().warning(warning));
            if (recipeAudit.warnings().size() > 80) {
                getLogger().warning("[SFX Recipe Import] ... and " + (recipeAudit.warnings().size() - 80) + " more warnings.");
            }
        }
    }

    private boolean syncBundledRecipeFiles() {
        return getConfig().getBoolean("content.sync-bundled-recipes-on-startup", false);
    }

    private boolean syncBundledItemFiles() {
        return getConfig().getBoolean("content.sync-bundled-items-on-startup", false);
    }

    private boolean syncBundledResearchFiles() {
        return getConfig().getBoolean("content.sync-bundled-researches-on-startup", false);
    }

    private void saveBundledLanguage(String language) {
        File target = new File(new File(getDataFolder(), "lang"), language + ".yml");
        boolean overwrite = getConfig().getBoolean("content.sync-bundled-languages-on-startup", false);
        if (!target.exists() || overwrite) {
            saveResource("lang/" + language + ".yml", overwrite);
        }
    }

    private void syncBundledLanguages() {
        saveBundledLanguage("zh-CN");
        saveBundledLanguage("en-US");
    }

    private void saveBundledTestingFile() {
        saveResource("TESTING.md", true);
    }

    private File playerDataFile() {
        String configured = getConfig().getString("storage.sqlite-file", "data/player-data.db");
        return new File(getDataFolder(), configured);
    }

    private File blockDataFile() {
        String configured = getConfig().getString("storage.block-data.sqlite-file", "data/block-data.db");
        return new File(getDataFolder(), configured);
    }

    private File gpsDataFile() {
        String configured = getConfig().getString("storage.gps-data.sqlite-file", "data/gps-data.db");
        return new File(getDataFolder(), configured);
    }

    private void invokePacketEventsApi(String methodName) throws Exception {
        if (packetEventsApi == null) {
            throw new IllegalStateException("PacketEvents API is not initialized");
        }
        Method method = packetEventsApi.getClass().getMethod(methodName);
        forceAccessible(method);
        method.invoke(packetEventsApi);
    }

    private Object invokeSingleArgStaticReturning(Class<?> target, String methodName, Object argument) throws Exception {
        for (Method method : target.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
                forceAccessible(method);
                return method.invoke(null, argument);
            }
        }
        throw new NoSuchMethodException(target.getName() + "." + methodName + "(<arg>)");
    }

    private void invokeSingleArgStatic(Class<?> target, String methodName, Object argument) throws Exception {
        for (Method method : target.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
                forceAccessible(method);
                method.invoke(null, argument);
                return;
            }
        }
        throw new NoSuchMethodException(target.getName() + "." + methodName + "(<api>)");
    }

    private void forceAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Some runtime/module configurations may reject setAccessible. Invocation can still succeed
            // for public members on public classes, so do not fail startup solely on this best-effort step.
        }
    }

    private void logPacketEventsStartupFailure(Throwable throwable) {
        Throwable cause = throwable;
        if (throwable instanceof java.lang.reflect.InvocationTargetException invocationTargetException && invocationTargetException.getTargetException() != null) {
            cause = invocationTargetException.getTargetException();
        }
        getLogger().severe("==================================================");
        getLogger().severe("SlimeFunX failed to start: PacketEvents is missing or incompatible.");
        getLogger().severe("Install a PacketEvents build compatible with this Paper/Folia server.");
        getLogger().severe("SFX uses PacketEvents for virtual floating text, packet displays and altar visuals.");
        getLogger().severe("Cause: " + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
        getLogger().severe("==================================================");
    }
}

