package cc.theends6.sfx;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.bootstrap.BaseContentBootstrap;
import cc.theends6.sfx.internal.bootstrap.SfxYamlContentLoader;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBasicMachineBlockListener;
import cc.theends6.sfx.internal.block.SfxPlaceableBlockListener;
import cc.theends6.sfx.internal.block.SfxBlockPersistenceListener;
import cc.theends6.sfx.internal.block.SqliteSfxBlockDataRepository;
import cc.theends6.sfx.internal.command.SfxCommand;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
import cc.theends6.sfx.internal.configurable.SfxConfigurableMachineService;
import cc.theends6.sfx.internal.cargo.SfxCargoService;
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
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
    private SfxRadiationService radiationService;
    private SfxTechnicalGadgetService technicalGadgetService;
    private SfxFloatingTextDisplayService floatingTextDisplayService;
    private boolean packetEventsLoaded;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
        packetEventsLoaded = true;
    }

    @Override
    public void onEnable() {
        if (packetEventsLoaded) {
            PacketEvents.getAPI().init();
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
        this.electricMachineService = new SfxElectricMachineService(this, api.runtime(), api.items(), localization, blockDataService, playerDataService, api.internalManualMachines());
        this.configurableMachineService = new SfxConfigurableMachineService(this, api.runtime(), api.items(), localization, blockDataService, floatingTextDisplayService);
        this.technicalGadgetService = new SfxTechnicalGadgetService(this, api.runtime(), api.items(), localization);
        this.energyService = new SfxEnergyService(this, api.runtime(), api.items(), localization, blockDataService, electricMachineService, configurableMachineService, floatingTextDisplayService, technicalGadgetService.rechargeableItems());
        this.virtualContainerService = new SfxVirtualContainerService(this, api.runtime());
        this.cargoService = new SfxCargoService(this, api.runtime(), api.items(), localization, blockDataService, virtualContainerService, floatingTextDisplayService);
        SfxPlaceableBlockListener placeableBlockListener = new SfxPlaceableBlockListener(api.items(), blockDataService, basicMachineBlockListener, electricMachineService, configurableMachineService, energyService, cargoService, api.runtime());
        this.blockPersistenceListener = new SfxBlockPersistenceListener(this, api.runtime(), blockDataService);
        this.radiationService = new SfxRadiationService(this, api.runtime(), api.items(), api.itemRegistry(), playerDataService);

        this.backpackListener = new SfxBackpackListener(this, api.runtime(), api.items(), localization, playerDataService, researchService);
        SfxLegacyUtilityListener utilityListener = new SfxLegacyUtilityListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig, blockDataService, radiationService);
        SfxLegacyCombatToolListener combatToolListener = new SfxLegacyCombatToolListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig, blockDataService);
        SfxLegacyFoodListener foodListener = new SfxLegacyFoodListener(this, api.runtime(), api.items(), localization);
        SfxTalismanListener talismanListener = new SfxTalismanListener(this, api.runtime(), api.items(), researchService);

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
        getServer().getPluginManager().registerEvents(technicalGadgetService, this);
        getServer().getPluginManager().registerEvents(backpackListener, this);
        getServer().getPluginManager().registerEvents(utilityListener, this);
        getServer().getPluginManager().registerEvents(combatToolListener, this);
        getServer().getPluginManager().registerEvents(foodListener, this);
        getServer().getPluginManager().registerEvents(talismanListener, this);
        getServer().getPluginManager().registerEvents(new SfxSoulboundListener(api.items(), researchService), this);
        getServer().getPluginManager().registerEvents(new SfxResearchFireworksListener(), this);
        getServer().getPluginManager().registerEvents(new SfxVanillaGuardListener(this, api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxArmorEffectListener(api.items()), this);
        getServer().getPluginManager().registerEvents(new SfxDebugJoinListener(this, api.runtime(), localization), this);
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
        if (blockPersistenceListener != null) {
            blockPersistenceListener.shutdown();
        }
        if (blockDataService != null) {
            blockDataService.shutdown();
        }
        if (packetEventsLoaded) {
            PacketEvents.getAPI().terminate();
            packetEventsLoaded = false;
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

        if (getConfig().getBoolean("debug-text.enabled", true)) {
            getLogger().info(recipeAudit.summary());
            recipeAudit.warnings().stream().limit(80).forEach(warning -> getLogger().warning(warning));
            if (recipeAudit.warnings().size() > 80) {
                getLogger().warning("[SFX Recipe Import] ... and " + (recipeAudit.warnings().size() - 80) + " more warnings.");
            }
        }
    }

    private boolean syncBundledRecipeFiles() {
        return getConfig().getBoolean("content.sync-bundled-recipes-on-startup", true);
    }

    private boolean syncBundledItemFiles() {
        return getConfig().getBoolean("content.sync-bundled-items-on-startup", true);
    }

    private boolean syncBundledResearchFiles() {
        return getConfig().getBoolean("content.sync-bundled-researches-on-startup", true);
    }

    private void saveBundledLanguage(String language) {
        File target = new File(new File(getDataFolder(), "lang"), language + ".yml");
        boolean overwrite = getConfig().getBoolean("content.sync-bundled-languages-on-startup", true);
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
}
