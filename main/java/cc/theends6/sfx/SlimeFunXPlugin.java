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
import cc.theends6.sfx.internal.core.SfxModuleManager;
import cc.theends6.sfx.internal.addon.SfxAddonManager;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;
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
import cc.theends6.sfx.internal.template.SfxTemplatePrecompiler;
import cc.theends6.sfx.internal.util.SfxLocalization;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlimeFunXPlugin extends JavaPlugin {
    private static final DateTimeFormatter OP_LIFECYCLE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    SfxApiImpl api;
    SfxLocalization localization;
    SfxLegacyItemBehaviorConfig legacyItemBehaviorConfig;
    SfxPlayerDataService playerDataService;
    SfxBlockDataService blockDataService;
    SfxBlockPersistenceListener blockPersistenceListener;
    SfxResearchRegistry researchRegistry;
    SfxResearchService researchService;
    SfxBackpackListener backpackListener;
    SfxBasicMachineBlockListener basicMachineBlockListener;
    SfxElectricMachineService electricMachineService;
    SfxConfigurableMachineService configurableMachineService;
    SfxEnergyService energyService;
    SfxVirtualContainerService virtualContainerService;
    SfxCargoService cargoService;
    SfxDecorationService decorationService;
    SfxGpsService gpsService;
    SfxAndroidService androidService;
    SfxRadiationService radiationService;
    SfxTechnicalGadgetService technicalGadgetService;
    SfxFloatingTextDisplayService floatingTextDisplayService;
    SfxAncientAltarService ancientAltarService;
    SfxSpawnerService spawnerService;
    SfxBlockPlacerService blockPlacerService;
    SfxInfusedHopperService infusedHopperService;
    SfxHologramProjectorService hologramProjectorService;
    SfxIndustrialMinerService industrialMinerService;
    SfxListenerRegistrar listenerRegistrar;
    SfxModuleManager moduleManager;
    DefaultSfxFeatureRegistry featureRegistry;
    DefaultSfxBehaviorRegistry behaviorRegistry;
    SfxAddonManager addonManager;
    SfxMachineRuntimeEngine machineRuntime;
    SfxMachinePhaseLedger machinePhaseLedger;
    Object packetEventsApi;
    boolean packetEventsLoaded;
    boolean packetEventsUnavailable;

    @Override
    public void onLoad() {
        try {
            Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents", true, getClassLoader());
            packetEventsApi = packetEventsClass.getMethod("getAPI").invoke(null);
            if (packetEventsApi == null) {
                throw new IllegalStateException("PacketEvents API is not initialized by the PacketEvents plugin");
            }
            packetEventsLoaded = true;
        } catch (Throwable throwable) {
            packetEventsUnavailable = true;
            logPacketEventsStartupFailure(throwable);
        }
    }

    @Override
    public void onEnable() {
        SfxPluginBootstrap.enable(this);
        notifyOnlineOps("plugin.lifecycle.enabled");
    }

    

    @Override
    public void onDisable() {
        Component disabledMessage = lifecycleMessage("plugin.lifecycle.disabled");
        try {
            SfxPluginBootstrap.disable(this);
        } finally {
            notifyOnlineOps(disabledMessage);
        }
    }

    

    public SfxApi api() {
        return api;
    }

    private void notifyOnlineOps(String key) {
        notifyOnlineOps(lifecycleMessage(key));
    }

    private Component lifecycleMessage(String key) {
        if (localization == null) {
            return null;
        }
        return localization.component(key, java.util.Map.of("time", LocalDateTime.now().format(OP_LIFECYCLE_TIME)));
    }

    private void notifyOnlineOps(Component message) {
        if (message == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(message);
            }
        }
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

    public SfxAddonManager addonManager() {
        return addonManager;
    }

    public SfxBackpackListener backpackListener() {
        return backpackListener;
    }

    public synchronized boolean reloadAllContent() {
        try {
            if (api != null) {
                api.menus().closeAll();
            }
            if (moduleManager != null) {
                moduleManager.disableAllReverse();
                moduleManager = null;
            }
            HandlerList.unregisterAll(this);
            cancelScheduledRuntimeTasks("runtime reload");
            if (machineRuntime != null) {
                cc.theends6.sfx.internal.machine.SfxWorldMutationBridge.clearDefaultRuntime(machineRuntime);
                machineRuntime.clear();
            }

            reloadConfig();
            syncBundledLanguages();
            legacyItemBehaviorConfig.reload();
            SfxPluginAddonModule.loadAddons(this);
            localization.reload();
            compileContentTemplates();
            if (researchRegistry != null) {
                researchRegistry.clear();
            }
            bootstrapContent();
            SfxPluginRuntimeModule.initialize(this);
            SfxPluginServices services = SfxPluginServiceModule.create(this);
            return SfxPluginStartupModule.start(this, services);
        } catch (RuntimeException exception) {
            getLogger().severe("SFX runtime reload failed: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    void bootstrapContent() {
        SfxContentBootstrapper.bootstrap(this, api, researchRegistry);
    }

    public cc.theends6.sfx.internal.template.SfxTemplateCompileReport compileContentTemplates() {
        return SfxTemplatePrecompiler.compile(this, syncBundledTemplateFiles());
    }

    boolean compileTemplatesOnStartup() {
        return getConfig().getBoolean("content.compile-templates-on-startup", false);
    }

    boolean syncBundledTemplateFiles() {
        return getConfig().getBoolean("content.sync-bundled-templates-before-compile", false);
    }

    boolean syncBundledRecipeFiles() {
        return getConfig().getBoolean("content.sync-bundled-recipes-on-startup", false);
    }

    boolean syncBundledItemFiles() {
        return getConfig().getBoolean("content.sync-bundled-items-on-startup", false);
    }

    boolean syncBundledResearchFiles() {
        return getConfig().getBoolean("content.sync-bundled-researches-on-startup", false);
    }

    void saveBundledLanguage(String language) {
        File target = new File(new File(getDataFolder(), "lang"), language + ".yml");
        boolean overwrite = getConfig().getBoolean("content.sync-bundled-languages-on-startup", false);
        if (!target.exists() || overwrite) {
            saveResource("lang/" + language + ".yml", overwrite);
        }
    }

    void syncBundledLanguages() {
        saveBundledLanguage("zh-CN");
        saveBundledLanguage("en-US");
    }

    void unregisterRuntimeHooks() {
        HandlerList.unregisterAll(this);
        cancelScheduledRuntimeTasks("plugin disable");
    }

    void clearRuntimeReferences() {
        blockPersistenceListener = null;
        backpackListener = null;
        basicMachineBlockListener = null;
        electricMachineService = null;
        configurableMachineService = null;
        energyService = null;
        virtualContainerService = null;
        cargoService = null;
        decorationService = null;
        gpsService = null;
        androidService = null;
        radiationService = null;
        technicalGadgetService = null;
        floatingTextDisplayService = null;
        ancientAltarService = null;
        spawnerService = null;
        blockPlacerService = null;
        infusedHopperService = null;
        hologramProjectorService = null;
        industrialMinerService = null;
        listenerRegistrar = null;
        moduleManager = null;
        if (addonManager != null) {
            addonManager.close();
            addonManager = null;
        }
        featureRegistry = null;
        behaviorRegistry = null;
        machineRuntime = null;
        machinePhaseLedger = null;
        researchService = null;
        researchRegistry = null;
        legacyItemBehaviorConfig = null;
        localization = null;
        api = null;
        playerDataService = null;
        blockDataService = null;
    }

    private void cancelScheduledRuntimeTasks(String reason) {
        try {
            getServer().getScheduler().cancelTasks(this);
        } catch (Throwable throwable) {
            getLogger().warning("Failed to cancel Bukkit scheduler tasks during " + reason + ": " + throwable.getMessage());
        }
        try {
            getServer().getGlobalRegionScheduler().cancelTasks(this);
        } catch (Throwable throwable) {
            getLogger().warning("Failed to cancel global region tasks during " + reason + ": " + throwable.getMessage());
        }
        try {
            Object regionScheduler = getServer().getRegionScheduler();
            Method cancelTasks = regionScheduler.getClass().getMethod("cancelTasks", org.bukkit.plugin.Plugin.class);
            cancelTasks.invoke(regionScheduler, this);
        } catch (NoSuchMethodException ignored) {
            
            
        } catch (Throwable throwable) {
            getLogger().warning("Failed to cancel region scheduler tasks during " + reason + ": " + throwable.getMessage());
        }
        try {
            getServer().getAsyncScheduler().cancelTasks(this);
        } catch (Throwable throwable) {
            getLogger().warning("Failed to cancel async scheduler tasks during " + reason + ": " + throwable.getMessage());
        }
    }

    File playerDataFile() {
        String configured = getConfig().getString("storage.sqlite-file", "data/player-data.db");
        return new File(getDataFolder(), configured);
    }

    File blockDataFile() {
        String configured = getConfig().getString("storage.block-data.sqlite-file", "data/block-data.db");
        return new File(getDataFolder(), configured);
    }

    File gpsDataFile() {
        String configured = getConfig().getString("storage.gps-data.sqlite-file", "data/gps-data.db");
        return new File(getDataFolder(), configured);
    }

    File androidScriptsFile() {
        String configured = getConfig().getString("storage.android-scripts.sqlite-file", "data/android-scripts.db");
        return new File(getDataFolder(), configured);
    }

    void invokePacketEventsApi(String methodName) throws Exception {
        if (packetEventsApi == null) {
            throw new IllegalStateException("PacketEvents API is not initialized");
        }
        Method method = packetEventsApi.getClass().getMethod(methodName);
        forceAccessible(method);
        method.invoke(packetEventsApi);
    }

    boolean packetEventsApiBoolean(String methodName) throws Exception {
        if (packetEventsApi == null) {
            throw new IllegalStateException("PacketEvents API is not initialized");
        }
        Method method = packetEventsApi.getClass().getMethod(methodName);
        forceAccessible(method);
        Object result = method.invoke(packetEventsApi);
        return result instanceof Boolean value && value;
    }

    Object invokeSingleArgStaticReturning(Class<?> target, String methodName, Object argument) throws Exception {
        for (Method method : target.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
                forceAccessible(method);
                return method.invoke(null, argument);
            }
        }
        throw new NoSuchMethodException(target.getName() + "." + methodName + "(<arg>)");
    }

    void invokeSingleArgStatic(Class<?> target, String methodName, Object argument) throws Exception {
        for (Method method : target.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
                forceAccessible(method);
                method.invoke(null, argument);
                return;
            }
        }
        throw new NoSuchMethodException(target.getName() + "." + methodName + "(<api>)");
    }

    void forceAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
            
            
        }
    }

    void logPacketEventsStartupFailure(Throwable throwable) {
        Throwable cause = throwable;
        if (throwable instanceof java.lang.reflect.InvocationTargetException invocationTargetException && invocationTargetException.getTargetException() != null) {
            cause = invocationTargetException.getTargetException();
        }
        getLogger().severe("==================================================");
        getLogger().severe("SlimeFunX failed to start: required dependency PacketEvents is missing or incompatible.");
        getLogger().severe("Install PacketEvents 2.12.1+ compatible with this Paper/Folia server before enabling SlimeFunX.");
        getLogger().severe("SFX uses PacketEvents for virtual floating text, packet displays and altar visuals.");
        getLogger().severe("Cause: " + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
        getLogger().severe("==================================================");
    }
}
