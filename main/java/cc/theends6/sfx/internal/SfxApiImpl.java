package cc.theends6.sfx.internal;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxApiImpl implements SfxApi {
    private final SfxRuntime runtime;
    private final DefaultSfxItemRegistry itemRegistry;
    private final DefaultSfxItems items;
    private final DefaultSfxMenus menus;
    private final DefaultSfxGuide guide;
    private final DefaultManualMachineRegistry manualMachines;
    private final DefaultSfxMachineRuntimeApi machineRuntime;

    private SfxApiImpl(
            SfxRuntime runtime,
            DefaultSfxItemRegistry itemRegistry,
            DefaultSfxItems items,
            DefaultSfxMenus menus,
            DefaultSfxGuide guide,
            DefaultManualMachineRegistry manualMachines,
            DefaultSfxMachineRuntimeApi machineRuntime
    ) {
        this.runtime = runtime;
        this.itemRegistry = itemRegistry;
        this.items = items;
        this.menus = menus;
        this.guide = guide;
        this.manualMachines = manualMachines;
        this.machineRuntime = machineRuntime;
    }

    public static SfxApiImpl bootstrap(JavaPlugin plugin, SfxLocalization localization, SfxPlayerDataService profiles, SfxResearchService researches) {
        PaperSfxRuntime runtime = new PaperSfxRuntime(plugin);
        DefaultSfxItemRegistry itemRegistry = new DefaultSfxItemRegistry();
        DefaultManualMachineRegistry manualMachines = new DefaultManualMachineRegistry();
        DefaultSfxItems items = new DefaultSfxItems(plugin, itemRegistry, localization);
        DefaultSfxMenus menus = new DefaultSfxMenus(runtime);
        DefaultSfxGuide guide = new DefaultSfxGuide(plugin, runtime, itemRegistry, items, menus, new PermissionGuideAccessPolicy(), manualMachines, localization, profiles, researches);
        return new SfxApiImpl(runtime, itemRegistry, items, menus, guide, manualMachines, new DefaultSfxMachineRuntimeApi());
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
    public SfxGuide guide() {
        return guide;
    }

    @Override
    public SfxManualMachineRegistry manualMachines() {
        return manualMachines;
    }

    @Override
    public SfxMachineRuntime machineRuntime() {
        return machineRuntime;
    }

    public void bindMachineRuntime(SfxMachineRuntimeEngine engine) {
        machineRuntime.bind(engine);
    }

    public DefaultManualMachineRegistry internalManualMachines() {
        return manualMachines;
    }
}
