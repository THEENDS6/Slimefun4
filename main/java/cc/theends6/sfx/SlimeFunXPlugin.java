package cc.theends6.sfx;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.internal.SfxApiImpl;
import cc.theends6.sfx.internal.bootstrap.BaseContentBootstrap;
import cc.theends6.sfx.internal.bootstrap.LegacySfImportBootstrap;
import cc.theends6.sfx.internal.bootstrap.SfxYamlContentLoader;
import cc.theends6.sfx.internal.command.SfxCommand;
import cc.theends6.sfx.internal.config.SfxLegacyItemBehaviorConfig;
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
import cc.theends6.sfx.internal.research.SfxResearchYamlLoader;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.io.File;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlimeFunXPlugin extends JavaPlugin {

    private SfxApiImpl api;
    private SfxLocalization localization;
    private SfxLegacyItemBehaviorConfig legacyItemBehaviorConfig;
    private SfxPlayerDataService playerDataService;
    private SfxResearchRegistry researchRegistry;
    private SfxResearchService researchService;
    private SfxBackpackListener backpackListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledLanguage("zh-CN");
        saveBundledLanguage("en-US");
        saveBundledTestingFile();

        var runtime = new cc.theends6.sfx.internal.runtime.PaperSfxRuntime(this);
        try {
            this.playerDataService = new SfxPlayerDataService(this, runtime, new SqliteSfxPlayerDataRepository(this, playerDataFile()));
            playerDataService.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize SFX player data storage", exception);
        }
        this.researchRegistry = new SfxResearchRegistry();
        this.researchService = new SfxResearchService(researchRegistry, playerDataService);

        this.localization = new SfxLocalization(this);
        this.legacyItemBehaviorConfig = new SfxLegacyItemBehaviorConfig(this);
        legacyItemBehaviorConfig.ensureDefaultFile();
        legacyItemBehaviorConfig.reload();
        this.api = SfxApiImpl.bootstrap(this, localization, playerDataService, researchService);

        bootstrapContent();

        ManualMachineService manualMachineService = new ManualMachineService(this, api.runtime(), api.internalManualMachines(), api.items(), localization);

        this.backpackListener = new SfxBackpackListener(this, api.runtime(), api.items(), localization, playerDataService, researchService);
        SfxLegacyUtilityListener utilityListener = new SfxLegacyUtilityListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig);
        SfxLegacyCombatToolListener combatToolListener = new SfxLegacyCombatToolListener(this, api.runtime(), api.items(), localization, legacyItemBehaviorConfig);
        SfxLegacyFoodListener foodListener = new SfxLegacyFoodListener(this, api.runtime(), api.items(), localization);
        SfxTalismanListener talismanListener = new SfxTalismanListener(this, api.runtime(), api.items(), researchService);

        getServer().getPluginManager().registerEvents(api.menus(), this);
        getServer().getPluginManager().registerEvents(new SfxPlayerProfileListener(playerDataService), this);
        getServer().getPluginManager().registerEvents(new SfxGuideListener(this, api.items(), api.guide()), this);
        getServer().getPluginManager().registerEvents(new SfxItemUseDispatcher(api.items(), backpackListener, utilityListener, combatToolListener, foodListener, researchService, localization), this);
        getServer().getPluginManager().registerEvents(new SfxManualMachineListener(manualMachineService), this);
        getServer().getPluginManager().registerEvents(new SfxManualMachineDeployListener(this, api.internalManualMachines(), localization), this);
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

        SfxCommand command = new SfxCommand(this, api);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("slimefunx"), "plugin.yml missing /slimefunx command");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info("SFX enabled. Registered " + api.itemRegistry().items().size()
                + " item definitions and " + api.manualMachines().machines().size() + " manual machines.");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            api.menus().closeAll();
        }
        if (backpackListener != null) {
            backpackListener.shutdown();
        }
        if (playerDataService != null) {
            playerDataService.shutdown();
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

    public SfxResearchService researchService() {
        return researchService;
    }

    public SfxBackpackListener backpackListener() {
        return backpackListener;
    }

    public synchronized void reloadAllContent() {
        reloadConfig();
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
        LegacySfImportBootstrap.register(itemRegistry);

        SfxYamlContentLoader yamlContentLoader = new SfxYamlContentLoader(this, itemRegistry);
        yamlContentLoader.ensureDefaultFiles();
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

    private boolean syncBundledResearchFiles() {
        return getConfig().getBoolean("content.sync-bundled-researches-on-startup", true);
    }

    private void saveBundledLanguage(String language) {
        File target = new File(new File(getDataFolder(), "lang"), language + ".yml");
        if (!target.exists()) {
            saveResource("lang/" + language + ".yml", false);
        }
    }

    private void saveBundledTestingFile() {
        saveResource("TESTING.md", true);
    }

    private File playerDataFile() {
        String configured = getConfig().getString("storage.sqlite-file", "data/player-data.db");
        return new File(getDataFolder(), configured);
    }
}
