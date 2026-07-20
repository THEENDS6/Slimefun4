package cc.theends6.sfx.internal;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.chat.SfxChatInputService;
import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.feature.SfxFeatureRegistry;
import cc.theends6.sfx.internal.behavior.DefaultSfxBehaviorRegistry;
import cc.theends6.sfx.internal.research.SfxResearchPaymentRouter;
import cc.theends6.sfx.internal.chat.DefaultSfxChatInputService;
import cc.theends6.sfx.internal.guide.DefaultSfxGuide;
import cc.theends6.sfx.internal.guide.PermissionGuideAccessPolicy;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import cc.theends6.sfx.internal.item.DefaultSfxItems;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import cc.theends6.sfx.internal.machine.DefaultSfxMachineRuntimeApi;
import cc.theends6.sfx.internal.machine.SfxMachineRuntimeEngine;
import cc.theends6.sfx.internal.menu.DefaultSfxMenus;
import cc.theends6.sfx.internal.playerdata.SfxPlayerDataService;
import cc.theends6.sfx.internal.research.SfxResearchService;
import cc.theends6.sfx.internal.runtime.PaperSfxRuntime;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.feature.DefaultSfxFeatureRegistry;
import cc.theends6.sfx.api.time.SfxServerActiveClock;
import cc.theends6.sfx.internal.time.DefaultSfxServerActiveClock;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.internal.power.DefaultSfxInventoryPowerRouter;
import cc.theends6.sfx.api.world.SfxProtectionService;
import cc.theends6.sfx.api.world.SfxWorldActionService;
import cc.theends6.sfx.internal.world.DefaultSfxWorldActions;
import cc.theends6.sfx.internal.world.DefaultSfxProtectionService;
import cc.theends6.sfx.api.addon.SfxAddonRuntime;
import cc.theends6.sfx.internal.addon.DefaultSfxAddonRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxApiImpl implements SfxApi {
    private final SfxRuntime runtime;
    private final DefaultSfxItemRegistry itemRegistry;
    private final DefaultSfxItems items;
    private final DefaultSfxMenus menus;
    private final DefaultSfxChatInputService chatInput;
    private final SfxLocalizationView localization;
    private final DefaultSfxGuide guide;
    private final SfxFeatureRegistry features;
    private final SfxBehaviorRegistry behaviors;
    private final DefaultManualMachineRegistry manualMachines;
    private final DefaultSfxMachineRuntimeApi machineRuntime;
    private final DefaultSfxServerActiveClock activeClock;
    private final SfxInventoryPowerRouter powerRouter;
    private final DefaultSfxWorldActions worldActions;
    private final SfxProtectionService protection;
    private final DefaultSfxAddonRuntime addonRuntime;

    private SfxApiImpl(
            SfxRuntime runtime,
            DefaultSfxItemRegistry itemRegistry,
            DefaultSfxItems items,
            DefaultSfxMenus menus,
            DefaultSfxChatInputService chatInput,
            SfxLocalizationView localization,
            DefaultSfxGuide guide,
            SfxFeatureRegistry features,
            SfxBehaviorRegistry behaviors,
            DefaultManualMachineRegistry manualMachines,
            DefaultSfxMachineRuntimeApi machineRuntime,
            DefaultSfxServerActiveClock activeClock,
            SfxInventoryPowerRouter powerRouter,
            DefaultSfxWorldActions worldActions,
            SfxProtectionService protection,
            DefaultSfxAddonRuntime addonRuntime
    ) {
        this.runtime = runtime;
        this.itemRegistry = itemRegistry;
        this.items = items;
        this.menus = menus;
        this.chatInput = chatInput;
        this.localization = localization;
        this.guide = guide;
        this.features = features;
        this.behaviors = behaviors;
        this.manualMachines = manualMachines;
        this.machineRuntime = machineRuntime;
        this.activeClock = activeClock;
        this.powerRouter = powerRouter;
        this.worldActions = worldActions;
        this.protection = protection;
        this.addonRuntime = addonRuntime;
    }

    public static SfxApiImpl bootstrap(JavaPlugin plugin, SfxLocalization localization, SfxPlayerDataService profiles,
                                       SfxResearchService researches, SfxResearchPaymentRouter researchPayments,
                                       DefaultSfxFeatureRegistry features, DefaultSfxBehaviorRegistry behaviors) {
        PaperSfxRuntime runtime = new PaperSfxRuntime(plugin);
        DefaultSfxItemRegistry itemRegistry = new DefaultSfxItemRegistry();
        DefaultManualMachineRegistry manualMachines = new DefaultManualMachineRegistry();
        DefaultSfxItems items = new DefaultSfxItems(plugin, itemRegistry, localization);
        DefaultSfxMenus menus = new DefaultSfxMenus(runtime);
        DefaultSfxChatInputService chatInput = new DefaultSfxChatInputService(runtime);
        DefaultSfxGuide guide = new DefaultSfxGuide(plugin, runtime, itemRegistry, items, menus, chatInput,
                new PermissionGuideAccessPolicy(), manualMachines, localization, profiles, researches, researchPayments);
        DefaultSfxServerActiveClock activeClock = new DefaultSfxServerActiveClock(plugin,
                plugin.getDataFolder().toPath().resolve("data/server-active-ticks.dat"));
        activeClock.start();
        DefaultSfxProtectionService protection = new DefaultSfxProtectionService(plugin);
        DefaultSfxWorldActions worldActions = new DefaultSfxWorldActions(plugin, runtime, protection);
        DefaultSfxInventoryPowerRouter powerRouter = new DefaultSfxInventoryPowerRouter();
        DefaultSfxAddonRuntime addonRuntime = new DefaultSfxAddonRuntime(plugin, runtime, items, activeClock, powerRouter);
        return new SfxApiImpl(runtime, itemRegistry, items, menus, chatInput, localization, guide, features, behaviors,
                manualMachines, new DefaultSfxMachineRuntimeApi(), activeClock,
                powerRouter, worldActions, protection, addonRuntime);
    }

    @Override
    public SfxRuntime runtime() {
        return runtime;
    }

    @Override
    public SfxItemRegistry itemRegistry() {
        return itemRegistry;
    }

    @Override
    public SfxItems items() {
        return items;
    }

    @Override
    public SfxMenus menus() {
        return menus;
    }

    @Override
    public SfxChatInputService chatInput() {
        return chatInput;
    }

    @Override
    public SfxLocalizationView localization() {
        return localization;
    }

    @Override
    public SfxGuide guide() {
        return guide;
    }

    @Override
    public SfxFeatureRegistry features() {
        return features;
    }

    @Override
    public SfxBehaviorRegistry behaviors() {
        return behaviors;
    }

    @Override
    public SfxManualMachineRegistry manualMachines() {
        return manualMachines;
    }

    @Override
    public SfxMachineRuntime machineRuntime() {
        return machineRuntime;
    }

    @Override public SfxServerActiveClock activeClock() { return activeClock; }
    @Override public SfxInventoryPowerRouter powerRouter() { return powerRouter; }
    @Override public SfxWorldActionService worldActions() { return worldActions; }
    @Override public SfxProtectionService protection() { return protection; }
    @Override public SfxAddonRuntime addonRuntime() { return addonRuntime; }

    public void shutdown() {
        addonRuntime.close();
        activeClock.close();
    }

    public void bindAddonManager(cc.theends6.sfx.internal.addon.SfxAddonManager manager) {
        addonRuntime.bind(manager);
    }

    public void bindMachineRuntime(SfxMachineRuntimeEngine engine) {
        machineRuntime.bind(engine);
    }

    public DefaultManualMachineRegistry internalManualMachines() {
        return manualMachines;
    }

    public DefaultSfxItemRegistry internalItemRegistry() {
        return itemRegistry;
    }

    public DefaultSfxGuide internalGuide() {
        return guide;
    }
}
