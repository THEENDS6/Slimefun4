package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.SfxApi;
import cc.theends6.sfx.api.behavior.SfxBehaviorRegistry;
import cc.theends6.sfx.api.chat.SfxChatInputService;
import cc.theends6.sfx.api.feature.SfxFeatureRegistry;
import cc.theends6.sfx.api.guide.SfxGuide;
import cc.theends6.sfx.api.item.SfxItemRegistry;
import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.machine.SfxMachineRuntime;
import cc.theends6.sfx.api.machine.SfxManualMachineRegistry;
import cc.theends6.sfx.api.menu.SfxMenus;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.api.time.SfxServerActiveClock;
import cc.theends6.sfx.api.power.SfxInventoryPowerRouter;
import cc.theends6.sfx.api.world.SfxProtectionService;
import cc.theends6.sfx.api.world.SfxWorldActionService;
import cc.theends6.sfx.api.addon.SfxAddonRuntime;


final class SfxScopedAddonApi implements SfxApi {
    private final SfxApi delegate;
    private final SfxItemRegistry items;
    private final SfxManualMachineRegistry manualMachines;

    SfxScopedAddonApi(SfxApi delegate, SfxItemRegistry items, SfxManualMachineRegistry manualMachines) {
        this.delegate = delegate;
        this.items = items;
        this.manualMachines = manualMachines;
    }

    @Override public SfxRuntime runtime() { return delegate.runtime(); }
    @Override public SfxItemRegistry itemRegistry() { return items; }
    @Override public SfxItems items() { return delegate.items(); }
    @Override public SfxMenus menus() { return delegate.menus(); }
    @Override public SfxChatInputService chatInput() { return delegate.chatInput(); }
    @Override public SfxLocalizationView localization() { return delegate.localization(); }
    @Override public SfxGuide guide() { return delegate.guide(); }
    @Override public SfxFeatureRegistry features() { return delegate.features(); }
    @Override public SfxBehaviorRegistry behaviors() { return delegate.behaviors(); }
    @Override public SfxManualMachineRegistry manualMachines() { return manualMachines; }
    @Override public SfxMachineRuntime machineRuntime() { return delegate.machineRuntime(); }
    @Override public SfxServerActiveClock activeClock() { return delegate.activeClock(); }
    @Override public SfxInventoryPowerRouter powerRouter() { return delegate.powerRouter(); }
    @Override public SfxWorldActionService worldActions() { return delegate.worldActions(); }
    @Override public SfxProtectionService protection() { return delegate.protection(); }
    @Override public cc.theends6.sfx.api.permission.SfxWorldPermissionService permissions() { return delegate.permissions(); }
    @Override public SfxAddonRuntime addonRuntime() { return delegate.addonRuntime(); }
}
